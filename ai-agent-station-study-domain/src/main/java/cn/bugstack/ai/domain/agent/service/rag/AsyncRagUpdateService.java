package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.domain.agent.adapter.repository.IRagUpdateRepository;
import cn.bugstack.ai.api.dto.TaskStatusResponseDTO;
import cn.bugstack.ai.domain.agent.service.IAsyncRagUpdateService;
import cn.bugstack.ai.domain.agent.service.IRagUpdateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步知识库更新服务
 * @author bugstack.cn
 * @description 异步知识库更新服务实现
 */
@Slf4j
@Service
public class AsyncRagUpdateService implements IAsyncRagUpdateService {

    @Resource
    private IRagUpdateRepository ragUpdateRepository;

    @Resource
    private IRagUpdateService ragUpdateService;

    // 任务和知识库ID的映射
    private final ConcurrentHashMap<String, List<String>> taskRagIdsMap = new ConcurrentHashMap<>();

    // 取消任务标志
    private final ConcurrentHashMap<String, Boolean> cancelFlags = new ConcurrentHashMap<>();

    @Override
    public String submitBatchUpdateTask(List<String> ragIds, String updateReason) {
        String taskId = "task_" + System.currentTimeMillis();
        
        // 创建任务记录
        ragUpdateRepository.createUpdateTask(taskId, ragIds, updateReason);
        
        // 保存任务和知识库ID的映射
        taskRagIdsMap.put(taskId, ragIds);
        
        // 异步执行任务
        processUpdateTask(taskId);
        
        log.info("提交异步批量更新任务: taskId={}, ragIds={}", taskId, ragIds);
        return taskId;
    }

    @Override
    @Async("ragUpdateExecutor")
    public void processUpdateTask(String taskId) {
        try {
            // 获取任务对应的知识库ID列表
            List<String> ragIds = taskRagIdsMap.get(taskId);
            if (ragIds == null || ragIds.isEmpty()) {
                log.error("任务对应的知识库ID列表不存在: {}", taskId);
                return;
            }
            
            // 查询任务信息
            TaskStatusResponseDTO taskStatus = ragUpdateRepository.queryTaskStatus(taskId);
            if (taskStatus == null) {
                log.error("任务不存在: {}", taskId);
                return;
            }
            
            // 更新任务状态为处理中
            ragUpdateRepository.updateTaskStatus(taskId, "PROCESSING", 0, 0, 0, null);
            
            int processed = 0;
            int failed = 0;
            
            for (String ragId : ragIds) {
                // 检查取消标志
                if (cancelFlags.getOrDefault(taskId, false)) {
                    log.info("任务被取消: taskId={}", taskId);
                    ragUpdateRepository.updateTaskStatus(taskId, "CANCELLED", 0, processed, failed, null);
                    return;
                }
                
                try {
                    // 处理单个知识库更新
                    boolean success = ragUpdateService.updateRagDocuments(ragId, null, "批量更新");
                    
                    if (success) {
                        processed++;
                    } else {
                        failed++;
                    }
                    
                    // 更新进度
                    int progress = (processed + failed) * 100 / ragIds.size();
                    ragUpdateRepository.updateTaskStatus(taskId, "PROCESSING", progress, processed, failed, null);
                    
                } catch (Exception e) {
                    log.error("处理知识库失败: ragId={}", ragId, e);
                    failed++;
                    int progress = (processed + failed) * 100 / ragIds.size();
                    ragUpdateRepository.updateTaskStatus(taskId, "PROCESSING", progress, processed, failed, null);
                }
            }
            
            // 更新任务状态为完成
            ragUpdateRepository.updateTaskStatus(taskId, "COMPLETED", 100, processed, failed, null);
            
            log.info("批量更新任务完成: taskId={}, processed={}, failed={}", taskId, processed, failed);
            
        } catch (Exception e) {
            log.error("批量更新任务失败: taskId={}", taskId, e);
            ragUpdateRepository.updateTaskStatus(taskId, "FAILED", 0, 0, 0, e.getMessage());
        } finally {
            // 清理
            cancelFlags.remove(taskId);
            taskRagIdsMap.remove(taskId);
        }
    }

    @Override
    public void updateTaskProgress(String taskId, int processed, int failed) {
        TaskStatusResponseDTO taskStatus = ragUpdateRepository.queryTaskStatus(taskId);
        if (taskStatus != null) {
            int progress = (processed + failed) * 100 / taskStatus.getTotalItems();
            ragUpdateRepository.updateTaskStatus(taskId, taskStatus.getStatus(), progress, processed, failed, null);
        }
    }

    @Override
    public TaskStatusResponseDTO queryTaskStatus(String taskId) {
        return ragUpdateRepository.queryTaskStatus(taskId);
    }

    @Override
    public boolean cancelTask(String taskId) {
        TaskStatusResponseDTO taskStatus = ragUpdateRepository.queryTaskStatus(taskId);
        if (taskStatus == null) {
            log.error("任务不存在: {}", taskId);
            return false;
        }
        
        if (!"PENDING".equals(taskStatus.getStatus()) && !"PROCESSING".equals(taskStatus.getStatus())) {
            log.warn("任务状态不允许取消: taskId={}, status={}", taskId, taskStatus.getStatus());
            return false;
        }
        
        // 设置取消标志
        cancelFlags.put(taskId, true);
        
        // 更新任务状态
        ragUpdateRepository.updateTaskStatus(taskId, "CANCELLED", taskStatus.getProgress(), 
                taskStatus.getProcessedItems(), taskStatus.getFailedItems(), null);
        
        log.info("任务已取消: taskId={}", taskId);
        return true;
    }

    @Override
    public boolean retryFailedTask(String taskId) {
        TaskStatusResponseDTO taskStatus = ragUpdateRepository.queryTaskStatus(taskId);
        if (taskStatus == null) {
            log.error("任务不存在: {}", taskId);
            return false;
        }
        
        if (!"FAILED".equals(taskStatus.getStatus())) {
            log.warn("任务状态不允许重试: taskId={}, status={}", taskId, taskStatus.getStatus());
            return false;
        }
        
        // 重置任务状态
        ragUpdateRepository.updateTaskStatus(taskId, "PENDING", 0, 0, 0, null);
        
        // 重新执行任务
        processUpdateTask(taskId);
        
        log.info("重试失败任务: taskId={}", taskId);
        return true;
    }

}
