package cn.bugstack.ai.domain.agent.adapter.repository;

import cn.bugstack.ai.domain.agent.model.valobj.*;

import java.util.List;
import java.util.Map;

/**
 * AiAgent 仓储接口
 */
public interface IAgentRepository {

    List<AiClientApiVO> queryAiClientApiVOListByClientIds(List<String> clientIdList);

    List<AiClientModelVO> AiClientModelVOByClientIds(List<String> clientIdList);

    List<AiClientToolMcpVO> AiClientToolMcpVOByClientIds(List<String> clientIdList);

    List<AiClientSystemPromptVO> AiClientSystemPromptVOByClientIds(List<String> clientIdList);

    Map<String, AiClientSystemPromptVO> queryAiClientSystemPromptMapByClientIds(List<String> clientIdList);

    List<AiClientAdvisorVO> AiClientAdvisorVOByClientIds(List<String> clientIdList);

    List<AiClientVO> AiClientVOByClientIds(List<String> clientIdList);

    List<AiClientApiVO> queryAiClientApiVOListByModelIds(List<String> modelIdList);

    List<AiClientModelVO> AiClientModelVOByModelIds(List<String> modelIdList);

    Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId);

    /**
     * 运行期取流程配置（按归属优先）：优先 ownerId 自己绑定的那份，没有则回落系统默认（owner 为空）。
     * <p>
     * ownerId 为空或该用户没绑定过时，退化为「系统默认链路」，与单参版本行为一致（除不再串别人的私有配置）。
     *
     * @param aiAgentId 智能体ID
     * @param ownerId   调用者 userId（来自 ExecuteCommandEntity，不能依赖 ThreadLocal：执行链路切了线程池）
     */
    Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId, String ownerId);

    AiAgentVO queryAiAgentByAgentId(String aiAgentId);

    List<AiAgentClientFlowConfigVO> queryAiAgentClientsByAgentId(String aiAgentId);

    /**
     * 取智能体的客户端流程配置（按归属优先），供固定流程执行链路使用。
     *
     * @param aiAgentId 智能体ID
     * @param ownerId   调用者 userId
     */
    List<AiAgentClientFlowConfigVO> queryAiAgentClientsByAgentId(String aiAgentId, String ownerId);

    /**
     * 批量取客户端归属（只回判定所需字段）。
     * <p>
     * 供「自建智能体必须使用自己的模型 Key」这类归属校验使用 —— 该规则属领域规则，
     * 必须能在领域层判定，而不是由 trigger 层直连 DAO。
     *
     * @param clientIds 客户端 id 列表
     * @return 归属列表；不存在的引用会被跳过（「引用不存在」由配置完整性校验负责提示）
     */
    List<AiResourceOwnerVO> queryClientOwners(List<String> clientIds);

    /**
     * 批量取模型归属（只回判定所需字段）；不存在的引用会被跳过。
     */
    List<AiResourceOwnerVO> queryModelOwners(List<String> modelIds);

    /**
     * 取客户端下挂的模型 id（{@code ai_client_config}：client → model）。
     * <p>
     * 归属校验语义与旧实现一致：<b>不过滤 status</b>，因为这里要回答的是
     * 「这条链路引用了谁的资源」，与资源当前是否启用在否无关。
     */
    List<String> queryModelIdsByClientIds(List<String> clientIds);

    /**
     * 取「某个用户自己绑定在该智能体上的」启用流程配置（{@code owner_id = ownerId} 的那几条）。
     * <p>
     * 与 {@link #queryAiAgentClientsByAgentId(String, String)} 的区别：后者按归属<b>优先回落</b>
     * （没绑过就退化成系统默认链路），本方法**只认他自己的绑定**，因此可用于判定
     * 「他到底绑没绑」以及「后端重启后需要补装哪些链路」。
     *
     * @param aiAgentId 智能体ID
     * @param ownerId   用户 userId
     */
    List<AiAgentClientFlowConfigVO> queryUserOwnFlowConfigs(String aiAgentId, String ownerId);

    /**
     * 取 API 通道的归属（不存在返回 null），供「装配 API 通道」的写权限校验使用。
     */
    AiResourceOwnerVO queryApiChannelOwner(String apiId);

    List<AiAgentTaskScheduleVO> queryAllValidTaskSchedule();

    List<Long> queryAllInvalidTaskScheduleIds();

    void createTagOrder(AiRagOrderVO aiRagOrderVO);

    /**
     * 查询可用的智能体列表
     * @return 可用的智能体列表
     */
    List<AiAgentVO> queryAvailableAgents();

    List<AiClientApiVO> queryAiClientApiVOListByApiIds(List<String> apiIdList);

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
     * 查询知识库配置
     * @param ragId 知识库ID
     * @return 知识库配置
     */
    AiRagOrderVO queryRagOrderById(String ragId);

}
