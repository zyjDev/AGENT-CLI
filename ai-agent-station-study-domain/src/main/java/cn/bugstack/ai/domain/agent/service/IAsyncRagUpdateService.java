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
     * 处理更新任务
     * @param taskId 任务ID
     */
    void processUpdateTask(String taskId);

    /**
     * 更新任务进度
     * @param taskId 任务ID
     * @param processed 已处理数量
     * @param failed 失败数量
     */
    void updateTaskProgress(String taskId, int processed, int failed);

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
