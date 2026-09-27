package cn.bugstack.ai.domain.agent.adapter.repository;

import cn.bugstack.ai.domain.agent.model.valobj.*;

import java.util.List;
import java.util.Map;

/**
 * AiAgent 仓储接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/6/27 16:48
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
