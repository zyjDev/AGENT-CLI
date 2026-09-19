package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.api.dto.AiClientRagOrderResponseDTO;
import cn.bugstack.ai.domain.agent.adapter.repository.IRagUpdateRepository;
import cn.bugstack.ai.domain.agent.service.IRagUpdateService;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.BizException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库更新服务实现
 * @author bugstack.cn
 * @description 知识库更新服务实现
 * <p>
 * 2026-09-19 收敛：删除了 4 个零调用的旧版方法（{@code incrementalUpdateRag} /
 * {@code asyncBatchUpdateRag} / {@code queryUpdateTaskStatus} / {@code rollbackRagVersion}）
 * 及其私有辅助方法 {@code executeBatchUpdateTask}。
 * 它们已被 {@code AsyncRagUpdateService}（批量更新 + 任务状态）与
 * {@code RollbackService}（版本回滚）取代，本类只保留 4 个真实在用的方法。
 */
@Slf4j
@Service
public class RagUpdateServiceImpl implements IRagUpdateService {

    @Resource
    private IRagUpdateRepository ragUpdateRepository;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private PgVectorStore vectorStore;

    @Override
    public List<AiClientRagOrderResponseDTO> queryUpdatedRagOrders(LocalDateTime updateTime) {
        return ragUpdateRepository.queryUpdatedRagOrders(updateTime);
    }

    @Override
    public List<AiClientRagOrderResponseDTO> queryAllRagOrders() {
        return ragUpdateRepository.queryAllRagOrders();
    }

    @Override
    public AiClientRagOrderResponseDTO queryRagOrderById(String ragId) {
        return ragUpdateRepository.queryRagOrderById(ragId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRagDocuments(String ragId, List<MultipartFile> files, String updateReason) {
        // ⚠️ 前置校验放在 try 之外：
        //    1) 参数错误不该被下面的 catch 包装成笼统的「更新知识库文档失败」，否则调用方看不到真实原因；
        //    2) 此时尚未做任何写入，也不必走事务回滚。
        //    原实现用 IllegalArgumentException，会被 catch 包成 RuntimeException，语义丢失。
        if (files == null || files.isEmpty()) {
            throw new BizException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "更新知识库必须至少上传一个文件: ragId=" + ragId);
        }
        try {
            // 1. 查询知识库配置
            AiClientRagOrderResponseDTO order = ragUpdateRepository.queryRagOrderById(ragId);

            if (order == null) {
                log.error("知识库配置不存在: {}", ragId);
                return false;
            }

            // 2. 保存当前版本到历史
            ragUpdateRepository.saveVersionHistory(
                ragId, 
                order.getVersion(), 
                order.getFileHash(), 
                order.getUpdateReason(),
                0
            );

            // 3. 处理新文件
            String newFileHash = calculateFileHash(files);
            
            // 4. 删除旧文档
            deleteOldDocuments(ragId, order.getKnowledgeTag());
            
            // 5. 分割并存储新文档
            int documentCount = saveNewDocuments(ragId, order.getKnowledgeTag(), files, newFileHash, updateReason);
            
            // 6. 更新配置
            boolean updateResult = ragUpdateRepository.updateRagOrder(
                ragId, 
                newFileHash, 
                updateReason, 
                order.getVersion() + 1
            );
            
            if (updateResult) {
                log.info("知识库更新成功: ragId={}, version={}, documentCount={}", ragId, order.getVersion() + 1, documentCount);
                return true;
            }
            
            return false;
            
        } catch (BizException e) {
            // 业务异常保持原样外抛，避免 code/message 被下面的 catch 覆盖成笼统的「更新知识库文档失败」
            throw e;
        } catch (Exception e) {
            log.error("更新知识库文档失败: ragId={}", ragId, e);
            throw new RuntimeException("更新知识库文档失败", e);
        }
    }

    /**
     * 计算文件哈希
     */
    private String calculateFileHash(List<MultipartFile> files) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (MultipartFile file : files) {
                md.update(file.getBytes());
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("计算文件哈希失败", e);
            throw new IllegalStateException("计算文件哈希失败", e);
        }
    }


    /**
     * 删除旧文档
     */
    private void deleteOldDocuments(String ragId, String knowledgeTag) {
        log.info("删除旧文档: ragId={}, knowledgeTag={}", ragId, knowledgeTag);
        org.springframework.ai.vectorstore.filter.FilterExpressionTextParser parser = new org.springframework.ai.vectorstore.filter.FilterExpressionTextParser();
        String filterExpr = String.format("knowledge == '%s' && ragId == '%s'", knowledgeTag, ragId);
        vectorStore.delete(parser.parse(filterExpr));
    }

    /**
     * 保存新文档
     */
    private int saveNewDocuments(String ragId, String knowledgeTag, List<MultipartFile> files, String fileHash, String updateReason) {
        int totalDocuments = 0;

        for (MultipartFile file : files) {
            try {
                TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
                List<Document> documentList = tokenTextSplitter.apply(documentReader.get());

                documentList.forEach(doc -> {
                    doc.getMetadata().put("knowledge", knowledgeTag);
                    doc.getMetadata().put("ragId", ragId);
                    doc.getMetadata().put("lastUpdateTime", LocalDateTime.now().toString());
                    doc.getMetadata().put("fileHash", fileHash);
                    doc.getMetadata().put("updateReason", updateReason);
                });

                vectorStore.accept(documentList);
                totalDocuments += documentList.size();

                log.info("保存文档: fileName={}, documentCount={}", file.getOriginalFilename(), documentList.size());
            } catch (Exception e) {
                log.error("保存文档失败: fileName={}", file.getOriginalFilename(), e);
                throw new RuntimeException("保存文档失败: " + file.getOriginalFilename(), e);
            }
        }

        return totalDocuments;
    }

}
