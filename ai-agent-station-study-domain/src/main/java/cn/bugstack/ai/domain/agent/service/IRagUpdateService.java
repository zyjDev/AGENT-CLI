package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.api.dto.AiClientRagOrderResponseDTO;
import cn.bugstack.ai.api.dto.TaskStatusResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库更新服务接口
 * @author bugstack.cn
 * @description 知识库更新服务接口
 */
public interface IRagUpdateService {

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
     * 更新知识库文档
     * @param ragId 知识库ID
     * @param files 新文件列表
     * @param updateReason 更新原因
     * @return 是否成功
     */
    boolean updateRagDocuments(String ragId, List<MultipartFile> files, String updateReason);

    /**
     * 增量更新知识库（基于 updateTime）
     * @param updateTime 更新时间阈值
     * @return 更新结果
     */
    boolean incrementalUpdateRag(LocalDateTime updateTime);

    /**
     * 异步批量更新知识库
     * @param ragIds 知识库ID列表
     * @param updateReason 更新原因
     * @return 任务ID
     */
    String asyncBatchUpdateRag(List<String> ragIds, String updateReason);

    /**
     * 查询更新任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    TaskStatusResponseDTO queryUpdateTaskStatus(String taskId);

    /**
     * 回滚知识库到指定版本
     * @param ragId 知识库ID
     * @param targetVersion 目标版本号
     * @return 是否成功
     */
    boolean rollbackRagVersion(String ragId, Integer targetVersion);

}
