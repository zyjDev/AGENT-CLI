package cn.bugstack.ai.api;

import cn.bugstack.ai.api.dto.AiClientApiBindRequestDTO;
import cn.bugstack.ai.api.dto.AiClientApiBoundAgentResponseDTO;
import cn.bugstack.ai.api.response.Response;

import java.util.List;

/**
 * 「用户自己的模型 Key ↔ 智能体」绑定接口。
 *
 * <p>产品规则：平台默认 Key 只给管理员用，普通用户要用某个智能体（包括 6 个基础智能体）
 * 必须先在「客户端 API 管理」里配好自己的 base_url + api_key，并绑定给它。
 * 绑定后运行时优先走用户自己的链路（见 IAgentRepository.queryAiAgentClientFlowConfig(agentId, ownerId)）。
 *
 * @author bugstack虫洞栈
 */
public interface IAiClientApiBindingAdminService {

    /**
     * 绑定：用 apiId 这条自己的密钥，给 agentId 接线一套属于调用者的链路，并立即装配。
     * 同一智能体重复绑定 = 覆盖。
     */
    Response<Boolean> bindAgent(AiClientApiBindRequestDTO request);

    /**
     * 解绑：删掉调用者在该智能体上的绑定行（他的 model / client 行保留）。
     * 解绑后该智能体对他不可用，必须重新绑定。
     */
    Response<Boolean> unbindAgent(AiClientApiBindRequestDTO request);

    /**
     * 查询：这条 apiId 目前绑定给了哪些智能体（用于列表展示与解绑入口）。
     */
    Response<List<AiClientApiBoundAgentResponseDTO>> queryBoundAgents(AiClientApiBindRequestDTO request);

}
