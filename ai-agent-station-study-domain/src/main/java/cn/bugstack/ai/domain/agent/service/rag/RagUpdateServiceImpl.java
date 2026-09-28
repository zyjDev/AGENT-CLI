package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.api.dto.AiClientRagOrderResponseDTO;
import cn.bugstack.ai.domain.agent.adapter.repository.IRagUpdateRepository;
import cn.bugstack.ai.domain.agent.service.IRagUpdateService;
import cn.bugstack.ai.types.common.OwnerScope;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.BizException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
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

    /**
     * 更新知识库文档（全量替换：删旧向量 → 写新向量 → 升级版本号）。
     *
     * <p>⚠️ **故意不加 `@Transactional`**（P1-5）：本方法跨 MySQL 与 pgvector 两个数据源，
     * pgvector 走独立的 `pgVectorJdbcTemplate`，与 MySQL 不在同一个事务管理器上 ——
     * 「跨库原子」在本项目物理上做不到（见审查报告 §6「有意不做」第 6 条）。
     * 在方法上挂 `@Transactional` 不会带来任何跨库保证，只会有两个副作用：
     * <ol>
     *   <li>删旧向量 / 写新向量（逐批网络 IO、耗时最长）被包进 MySQL 事务，
     *       长时间持有连接与行锁；</li>
     *   <li>回滚只回滚 MySQL —— 向量此时已经被换掉了，回滚反而把「元数据」与「向量」
     *       拉得更远（元数据说旧版本、向量里是新内容），比不回滚更糟。</li>
     * </ol>
     *
     * <p><b>去事务后的真实失败语义（按执行顺序，必须知悉）：</b>
     * <ol>
     *   <li><b>写版本历史</b>（MySQL 单条 insert，独立提交）：失败则整批终止，尚未动向量，无副作用。</li>
     *   <li><b>删旧向量 → 写新向量</b>：<b>这是唯一不可补偿的窗口</b>。此处失败则旧内容已删、
     *       新内容只写了一半，两边都无法自动恢复（原始文件未持久化到 MySQL 或对象存储，
     *       没有重建来源）。当前只能靠日志 + 调用方重新上传；
     *       根治办法是引入 `vector_status`（PENDING / DONE / FAILED）中间状态 + 补偿任务，
     *       让不一致可观测、可重试（审查报告 P1-5 建议 2，本次未做）。</li>
     *   <li><b>更新知识库配置</b>（MySQL 单条 update，独立提交）：失败则历史表有记录、
     *       配置版本未升级，属可对账的偏保守状态（不会把「未完成的新内容」当成「已生效」）。</li>
     * </ol>
     */
    @Override
    public boolean updateRagDocuments(String ragId, List<MultipartFile> files, String updateReason) {
        // ⚠️ 前置校验放在 try 之外：
        //    1) 参数错误不该被下面的 catch 包装成笼统的「更新知识库文档失败」，否则调用方看不到真实原因；
        //    2) 此时尚未做任何写入（本方法已无方法级事务，见上方说明），失败就是干净的失败。
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
            //    归属从**知识库记录**取，而不是从线程上下文取 —— 批量更新走异步线程池，
            //    那里 UserContext 是空的，取上下文会把私有库的 chunk 标成公共。
            int documentCount = saveNewDocuments(ragId, order.getKnowledgeTag(), order.getOwnerId(), files, newFileHash, updateReason);
            
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
    private int saveNewDocuments(String ragId, String knowledgeTag, String ownerId, List<MultipartFile> files, String fileHash, String updateReason) {
        int totalDocuments = 0;

        for (MultipartFile file : files) {
            try {
                TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
                List<Document> documentList = tokenTextSplitter.apply(documentReader.get());

                int chunkTotal = documentList.size();
                for (int i = 0; i < chunkTotal; i++) {
                    Document doc = documentList.get(i);
                    doc.getMetadata().put("knowledge", knowledgeTag);
                    doc.getMetadata().put("ragId", ragId);
                    // 归属：向量 metadata 无 NULL 语义，公共知识库统一写 __public__（见 OwnerScope）
                    doc.getMetadata().put(OwnerScope.VECTOR_OWNER_FIELD, OwnerScope.vectorOwnerOf(ownerId));
                    doc.getMetadata().put("lastUpdateTime", LocalDateTime.now().toString());
                    doc.getMetadata().put("fileHash", fileHash);
                    doc.getMetadata().put("updateReason", updateReason);
                    // 与 RagService.storeRagFile 保持同一套 metadata 契约（为检索侧「邻居扩展」预留）
                    doc.getMetadata().put("chunkIndex", i);
                    doc.getMetadata().put("chunkTotal", chunkTotal);
                }

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
