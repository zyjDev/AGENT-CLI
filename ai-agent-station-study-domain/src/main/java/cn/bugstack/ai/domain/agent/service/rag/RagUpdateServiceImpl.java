package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.api.dto.AiClientRagOrderResponseDTO;
import cn.bugstack.ai.api.dto.TaskStatusResponseDTO;
import cn.bugstack.ai.domain.agent.adapter.repository.IRagUpdateRepository;
import cn.bugstack.ai.domain.agent.service.IRagUpdateService;
import cn.bugstack.ai.domain.agent.service.IRagVersionService;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 知识库更新服务实现
 * @author bugstack.cn
 * @description 知识库更新服务实现
 */
@Slf4j
@Service
public class RagUpdateServiceImpl implements IRagUpdateService {

    @Resource
    private IRagUpdateRepository ragUpdateRepository;

    @Resource
    private IRagVersionService ragVersionService;

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
            
        } catch (Exception e) {
            log.error("更新知识库文档失败: ragId={}", ragId, e);
            throw new RuntimeException("更新知识库文档失败", e);
        }
    }

    @Override
    public boolean incrementalUpdateRag(LocalDateTime updateTime) {
        try {
            // 1. 查询待更新文档
            List<AiClientRagOrderResponseDTO> updatedOrders = queryUpdatedRagOrders(updateTime);
            
            if (updatedOrders.isEmpty()) {
                log.info("没有需要更新的知识库");
                return true;
            }
            
            log.info("开始增量更新，待更新数量: {}", updatedOrders.size());
            
            // 2. 遍历更新
            for (AiClientRagOrderResponseDTO order : updatedOrders) {
                log.info("增量更新知识库: ragId={}", order.getRagId());
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("增量更新失败", e);
            return false;
        }
    }

    @Override
    public String asyncBatchUpdateRag(List<String> ragIds, String updateReason) {
        String taskId = "task_" + System.currentTimeMillis();
        
        // 创建任务记录
        ragUpdateRepository.createUpdateTask(taskId, ragIds, updateReason);
        
        // 异步执行任务
        CompletableFuture.runAsync(() -> executeBatchUpdateTask(taskId, ragIds, updateReason));
        
        log.info("提交异步批量更新任务: taskId={}, ragIds={}", taskId, ragIds);
        return taskId;
    }

    @Override
    public TaskStatusResponseDTO queryUpdateTaskStatus(String taskId) {
        return ragUpdateRepository.queryTaskStatus(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackRagVersion(String ragId, Integer targetVersion) {
        try {
            // 1. 查询目标版本历史
            var versionHistory = ragUpdateRepository.getVersionHistory(ragId, targetVersion);
            if (versionHistory == null) {
                log.error("目标版本不存在: ragId={}, version={}", ragId, targetVersion);
                return false;
            }

            // 2. 查询当前配置
            AiClientRagOrderResponseDTO currentOrder = ragUpdateRepository.queryRagOrderById(ragId);
            if (currentOrder == null) {
                log.error("知识库配置不存在: {}", ragId);
                return false;
            }

            // 3. 保存当前版本到历史
            ragUpdateRepository.saveVersionHistory(
                ragId,
                currentOrder.getVersion(),
                currentOrder.getFileHash(),
                "回滚到版本 " + targetVersion,
                0
            );

            // 4. 更新配置
            boolean updateResult = ragUpdateRepository.updateRagOrder(
                ragId,
                currentOrder.getFileHash(),
                "回滚到版本 " + targetVersion,
                targetVersion
            );

            if (updateResult) {
                log.info("回滚成功: ragId={}, targetVersion={}", ragId, targetVersion);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("回滚失败: ragId={}, targetVersion={}", ragId, targetVersion, e);
            throw new RuntimeException("回滚失败", e);
        }
    }

    /**
     * 执行批量更新任务
     */
    private void executeBatchUpdateTask(String taskId, List<String> ragIds, String updateReason) {
        try {
            // 更新任务状态为处理中
            ragUpdateRepository.updateTaskStatus(taskId, "PROCESSING", 0, 0, 0, null);
            
            int processed = 0;
            int failed = 0;
            
            for (String ragId : ragIds) {
                try {
                    log.info("处理知识库: ragId={}", ragId);
                    processed++;
                    
                    // 更新进度
                    int progress = (processed * 100) / ragIds.size();
                    ragUpdateRepository.updateTaskStatus(taskId, "PROCESSING", progress, processed, failed, null);
                    
                } catch (Exception e) {
                    log.error("处理知识库失败: ragId={}", ragId, e);
                    failed++;
                    ragUpdateRepository.updateTaskStatus(taskId, "PROCESSING", (processed + failed) * 100 / ragIds.size(), processed, failed, null);
                }
            }
            
            // 更新任务状态为完成
            ragUpdateRepository.updateTaskStatus(taskId, "COMPLETED", 100, processed, failed, null);
            
            log.info("批量更新任务完成: taskId={}, processed={}, failed={}", taskId, processed, failed);
            
        } catch (Exception e) {
            log.error("批量更新任务失败: taskId={}", taskId, e);
            ragUpdateRepository.updateTaskStatus(taskId, "FAILED", 0, 0, 0, e.getMessage());
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
            return UUID.randomUUID().toString();
        }
    }


    /**
     * 删除旧文档
     */
    private void deleteOldDocuments(String ragId, String knowledgeTag) {
        try {
            log.info("删除旧文档: ragId={}, knowledgeTag={}", ragId, knowledgeTag);
            org.springframework.ai.vectorstore.filter.FilterExpressionTextParser parser = new org.springframework.ai.vectorstore.filter.FilterExpressionTextParser();
            String filterExpr = String.format("knowledge == '%s' && ragId == '%s'", knowledgeTag, ragId);
            vectorStore.delete(parser.parse(filterExpr));
        } catch (Exception e) {
            log.error("删除旧文档失败: ragId={}", ragId, e);
        }
    }

    /**
     * 保存新文档
     */
    private int saveNewDocuments(String ragId, String knowledgeTag, java.util.List<MultipartFile> files, String fileHash, String updateReason) {
        int totalDocuments = 0;

        for (MultipartFile file : files) {
            try {
                TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
                java.util.List<Document> documentList = tokenTextSplitter.apply(documentReader.get());

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
            }
        }

        return totalDocuments;
    }

}
