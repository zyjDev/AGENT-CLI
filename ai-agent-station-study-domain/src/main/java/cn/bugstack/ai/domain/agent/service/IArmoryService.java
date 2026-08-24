package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentVO;

import java.util.List;

/**
 * 装配接口
 */
public interface IArmoryService {
    /**
     * 装配所有可用的智能体
     * @return 可用的智能体列表
     */
    List<AiAgentVO> acceptArmoryAllAvailableAgents();

    /**
     * 装配指定智能体
     * @param agentId 智能体ID
     */
    void acceptArmoryAgent(String agentId);

    /**
     * 查询可用的智能体列表
     * @return 可用的智能体列表
     */
    List<AiAgentVO> queryAvailableAgents();

    /**
     * 装配指定智能体的客户端模型API
     * @param agentId 智能体ID
     */
    void acceptArmoryAgentClientModelApi(String agentId);

}
