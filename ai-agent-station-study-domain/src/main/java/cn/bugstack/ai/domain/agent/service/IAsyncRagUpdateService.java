package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.api.dto.TaskStatusResponseDTO;

import java.util.List;

/**
 * 异步知识库更新服务接口
 * @author bugstack.cn
 * @description 异步知识库更新服务接口
 */
public interface IAsyncRagUpdateService {

    /**
     * 提交批量更新任务
     * @param ragIds 知识库ID列表
     * @param updateReason 更新原因
     * @return 任务ID
     */
    String submitBatchUpdateTask(List<String> ragIds, String updateReason);

    /**
     * 处理更新任务。
     * <p>
     * 为什么 ragIds 要作为入参、而不是从服务内部的内存 Map 取：
     * 该 Map 会在任务结束的 finally 中清理，导致 {@code retryFailedTask} 重新执行时取不到 ragIds，
     * 任务被置为 PENDING 后再无人推进、永远卡死。改为显式传参后，重试路径从库
     * （{@code IRagUpdateRepository#queryTaskRagIds}）取回 ragIds 再调用即可。
     * <p>
     * 注意：本方法靠 {@code @Async} 异步执行，而 {@code @Async} 依赖 Spring 代理 ——
     * 类内 {@code this.xxx(...)} 自调用会绕过代理使注解失效，必须经自身代理引用调用。
     *
     * @param taskId 任务ID
     * @param ragIds 该任务关联的知识库ID列表
     */
    void processUpdateTask(String taskId, List<String> ragIds);

    /**
     * 查询任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    TaskStatusResponseDTO queryTaskStatus(String taskId);

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean cancelTask(String taskId);

    /**
     * 重试失败任务
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean retryFailedTask(String taskId);

}
