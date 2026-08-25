package cn.bugstack.ai.domain.agent.adapter.repository;

import cn.bugstack.ai.api.dto.VersionHistoryDTO;



import cn.bugstack.ai.api.dto.AiClientRagOrderResponseDTO;
import cn.bugstack.ai.api.dto.TaskStatusResponseDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库更新仓储接口
 * @author bugstack.cn
 * @description 知识库更新仓储接口
 */
public interface IRagUpdateRepository {

    /**
     * 查询指定时间之后更新的知识库配置
     * @param updateTime 更新时间
     * @return 知识库配置列表
     */
    List<AiClientRagOrderResponseDTO> queryUpdatedRagOrders(LocalDateTime updateTime);

    /**
     * 查询所有知识库配置
     * @return 知识库配置列表
     */
    List<AiClientRagOrderResponseDTO> queryAllRagOrders();

    /**
     * 根据知识库ID查询配置
     * @param ragId 知识库ID
     * @return 知识库配置
     */
    AiClientRagOrderResponseDTO queryRagOrderById(String ragId);

    /**
     * 更新知识库配置
     * @param ragId 知识库ID
     * @param fileHash 文件哈希
     * @param updateReason 更新原因
     * @param version 版本号
     * @return 是否成功
     */
    boolean updateRagOrder(String ragId, String fileHash, String updateReason, Integer version);

    /**
     * 保存版本历史
     * @param ragId 知识库ID
     * @param version 版本号
     * @param fileHash 文件哈希
     * @param updateReason 更新原因
     * @param documentCount 文档数量
     */
    void saveVersionHistory(String ragId, Integer version, String fileHash, String updateReason, Integer documentCount);

    /**
     * 获取知识库版本历史
     * @param ragId 知识库ID
     * @return 版本历史列表
     */
    List<VersionHistoryDTO> getVersionHistory(String ragId);

    /**
     * 获取最新版本号
     * @param ragId 知识库ID
     * @return 最新版本号
     */
    Integer getLatestVersion(String ragId);

    /**
     * 获取指定版本的历史记录
     * @param ragId 知识库ID
     * @param version 版本号
     * @return 版本历史记录
     */
    Object getVersionHistory(String ragId, Integer version);

    /**
     * 创建更新任务
     * @param taskId 任务ID
     * @param ragIds 知识库ID列表
     * @param updateReason 更新原因
     */
    void createUpdateTask(String taskId, List<String> ragIds, String updateReason);

    /**
     * 更新任务状态
     * @param taskId 任务ID
     * @param status 状态
     * @param progress 进度
     * @param processedItems 已处理数量
     * @param failedItems 失败数量
     * @param errorMessage 错误信息
     */
    void updateTaskStatus(String taskId, String status, Integer progress, Integer processedItems, Integer failedItems, String errorMessage);

    /**
     * 查询任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    TaskStatusResponseDTO queryTaskStatus(String taskId);

}
