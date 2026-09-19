package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.domain.agent.adapter.repository.IRagUpdateRepository;
import cn.bugstack.ai.api.dto.TaskStatusResponseDTO;
import cn.bugstack.ai.domain.agent.service.IAsyncRagUpdateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * 自身代理引用。
     * <p>
     * {@code @Async} 依赖 Spring 代理生效，而类内 {@code this.processUpdateTask(...)} 会绕过代理 ——
     * 原实现正是如此，导致注解形同虚设：submitBatchUpdateTask 会同步阻塞到整批跑完才返回，
     * 「异步」从未真正生效。用 {@code @Lazy} 自注入拿到代理对象，再经代理调用即可让 {@code @Async} 生效。
     */
    @Autowired
    @Lazy
    private IAsyncRagUpdateService self;

    @Override
    public String submitBatchUpdateTask(List<String> ragIds, String updateReason) {
        String taskId = "task_" + System.currentTimeMillis();

        // 创建任务记录
        ragUpdateRepository.createUpdateTask(taskId, ragIds, updateReason);

        // 异步执行任务（必须经 self 代理调用，直接 this 调用会让 @Async 失效）
        try {
            self.processUpdateTask(taskId, ragIds);
        } catch (Exception e) {
            // 提交失败（如线程池队列满被拒绝）时任务会永远停在 PENDING，这里同步收敛状态。
            // 注：ragIds 现在由入参传递，不再需要清理内存映射。
            ragUpdateRepository.updateTaskStatus(taskId, "FAILED", 0, 0, 0, "任务提交失败: " + e.getMessage());
            log.error("提交异步批量更新任务失败: taskId={}", taskId, e);
            throw e;
        }

        log.info("提交异步批量更新任务: taskId={}, ragIds={}", taskId, ragIds);
        return taskId;
    }

    @Override
    @Async("ragUpdateExecutor")
    public void processUpdateTask(String taskId, List<String> ragIds) {
        try {
            if (ragIds == null || ragIds.isEmpty()) {
                // 原实现这里只 log.error 后直接 return —— 任务行已创建却再无人推进，会永远停在 PENDING。
                // 改为显式收敛状态，让调用方查得到失败原因。
                ragUpdateRepository.updateTaskStatus(taskId, "FAILED", 0, 0, 0, "任务没有关联任何知识库ID");
                log.error("任务没有关联任何知识库ID: {}", taskId);
                return;
            }

            // 查询任务信息
            TaskStatusResponseDTO taskStatus = ragUpdateRepository.queryTaskStatus(taskId);
            if (taskStatus == null) {
                log.error("任务不存在: {}", taskId);
                return;
            }

            // ⚠️ 本能力未实现，原实现是「假失败」：
            //    批量更新需要「原始文件」才能重建向量，但项目并未持久化上传的文件内容
            //    （ai_client_rag_order 只保存 fileHash）。原实现对每个 ragId 调用
            //    updateRagDocuments(ragId, null, ...)，而该方法对 files == null 抛 IllegalArgumentException，
            //    结果是整批必然全失败，且每个 ragId 都要白跑一遍异常路径，最终任务被标 FAILED 但原因不可读。
            //    现改为一次性快速失败并给出可执行的原因，不再逐个 ragId 重试。
            //    恢复实现的前提：先把原始文件落盘（或对象存储），并在 updateRagDocuments 中按 ragId 读回。
            String reason = "批量更新未实现：更新知识库必须提供原始文件，而本服务未持久化上传的文件内容"
                    + "（ai_client_rag_order 仅保存 fileHash）。请改用 POST /api/v1/rag/update 逐个知识库上传文件更新。";
            ragUpdateRepository.updateTaskStatus(taskId, "FAILED", 0, 0, ragIds.size(), reason);
            log.warn("批量更新任务快速失败（能力未实现）: taskId={}, ragIds={}", taskId, ragIds);

        } catch (Exception e) {
            log.error("批量更新任务失败: taskId={}", taskId, e);
            ragUpdateRepository.updateTaskStatus(taskId, "FAILED", 0, 0, 0, e.getMessage());
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

        // 只改状态即可：执行体本身是一次性的快速失败（批量更新能力未实现），没有可中断的循环，
        // 故原先那个「写进 ConcurrentHashMap 却无人读取」的 cancelFlags 已删除 —— 它从未影响过任何行为。
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

        // 必须从库取回 ragIds：原实现依赖服务内存里的 Map，而该 Map 已在上一轮的 finally 中清理，
        // 导致重试时取不到 ragIds、任务被置为 PENDING 后再无人推进而永远卡死。
        List<String> ragIds = ragUpdateRepository.queryTaskRagIds(taskId);
        if (ragIds.isEmpty()) {
            ragUpdateRepository.updateTaskStatus(taskId, "FAILED", 0, 0, 0,
                    "重试失败：任务记录里没有关联的知识库ID");
            log.error("重试失败，任务记录里没有关联的知识库ID: taskId={}", taskId);
            return false;
        }

        // 重置任务状态
        ragUpdateRepository.updateTaskStatus(taskId, "PENDING", 0, 0, 0, null);

        // 重新执行任务（同样必须经代理调用，否则 @Async 失效）
        self.processUpdateTask(taskId, ragIds);

        log.info("重试失败任务: taskId={}, ragIds={}", taskId, ragIds);
        return true;
    }

}
