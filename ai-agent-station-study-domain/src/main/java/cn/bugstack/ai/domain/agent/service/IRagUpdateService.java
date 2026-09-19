package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.api.dto.AiClientRagOrderResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库更新服务接口
 * @author bugstack.cn
 * @description 知识库更新服务接口
 * <p>
 * 2026-09-19 收敛：原声明 8 个方法，实测只有下面 4 个真正被 {@code AiRagUpdateController} 调用。
 * 其余 4 个（{@code incrementalUpdateRag} / {@code asyncBatchUpdateRag} /
 * {@code queryUpdateTaskStatus} / {@code rollbackRagVersion}）是本接口的**旧版实现**，
 * 已被下列新服务取代 —— Controller 里同名的 endpoint 调的都是新服务，旧方法全仓库零调用：
 * <ul>
 *   <li>批量更新 / 任务状态查询 → {@link IAsyncRagUpdateService}</li>
 *   <li>版本回滚 → {@link IRollbackService}</li>
 * </ul>
 * 保留旧方法只会让「同名但不同实现」的两套代码长期并存，故一并删除。
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
     * <p>
     * 这是当前**唯一**能真正重建向量的入口：它按上传的文件重新切分并写入 pgvector。
     * 因此 {@code files} 为 null 或空时会显式抛业务异常，而不是静默失败。
     * @param ragId 知识库ID
     * @param files 新文件列表
     * @param updateReason 更新原因
     * @return 是否成功
     */
    boolean updateRagDocuments(String ragId, List<MultipartFile> files, String updateReason);

}
