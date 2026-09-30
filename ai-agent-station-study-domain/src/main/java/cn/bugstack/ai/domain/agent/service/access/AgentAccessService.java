package cn.bugstack.ai.domain.agent.service.access;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentAccessVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiResourceOwnerVO;
import cn.bugstack.ai.domain.agent.service.IAgentAccessService;
import cn.bugstack.ai.types.common.OwnerScope;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 智能体资源归属与可执行性校验实现。
 *
 * ============================ 规则（与业务确认，2026-09-27）============================
 * 1. <b>管理员</b>不受「自带 Key / 必须绑定」两条限制：他维护平台默认链路（owner_id 为空 = 人人可用）。
 * 2. <b>普通用户自己建的</b>智能体，链路上的「客户端 / 模型」必须**都是他自己的** ——
 *    owner_id 为空（平台默认）或属于别人，都算"借道"，一律拦下。
 * 3. <b>平台默认智能体</b>：普通用户必须已绑定自己的 Key（绑定关系落在
 *    {@code ai_agent_flow_config} 的 agent_id + client_id + owner_id 上）。
 *
 * 为什么必须拦：模型这一层直接对应 {@code ai_client_api} 里的 base_url + api_key，
 * 借道公共模型 = 消耗管理员的额度。
 * =====================================================================================
 *
 * <p>本类只依赖 {@link IAgentRepository}（领域端口），不碰任何 DAO —— 这正是把规则
 * 从 HTTP 层收回领域层的意义：换协议、加缓存、写单测都不再受表现层形态牵制。
 */
@Slf4j
@Service
public class AgentAccessService implements IAgentAccessService {

    /** 提示里必须告诉用户去哪儿配，否则报错等于没给出口 */
    private static final String GUIDE = "请到「客户端 API 管理」配置你自己的 base_url 与 API Key";

    @Resource
    private IAgentRepository agentRepository;

    @Override
    public AiAgentAccessVO resolveAccess(String agentId, String userId, boolean admin) {
        if (!hasText(agentId)) {
            return AiAgentAccessVO.builder().accessible(false).build();
        }

        // 一次查询：下面三条结论共用它，链路上不再重复查库（改造前同请求查了 3 次）
        AiAgentVO agent = agentRepository.queryAiAgentByAgentId(agentId);
        if (agent == null || !OwnerScope.isVisible(agent.getOwnerId(), userId)) {
            return AiAgentAccessVO.builder().accessible(false).build();
        }

        if (admin || !hasText(userId)) {
            return AiAgentAccessVO.builder().accessible(true).build();
        }

        String ownerId = agent.getOwnerId();
        if (isOwnedBy(ownerId, userId)) {
            // 他自己建的智能体：校验链路上的客户端 / 模型是否都是他自己的
            return AiAgentAccessVO.builder()
                    .accessible(true)
                    .ownModelProblem(checkAgentChain(agentId, userId))
                    .build();
        }

        // 走到这里 ownerId 必然为空（别人的私有智能体已在上面的 isVisible 被拦下）= 平台默认智能体：
        // 要求他先绑定自己的 Key（平台默认 Key 只给管理员用）
        return AiAgentAccessVO.builder()
                .accessible(true)
                .bindingProblem(checkBindingRequired(agentId, userId))
                .build();
    }

    @Override
    public String checkClients(Collection<String> clientIds, String ownerId) {
        if (clientIds == null || clientIds.isEmpty()) {
            return null;
        }
        List<AiResourceOwnerVO> owners = agentRepository.queryClientOwners(new ArrayList<>(clientIds));
        for (AiResourceOwnerVO owner : owners) {
            if (isOwnedBy(owner.getOwnerId(), ownerId)) {
                continue;
            }
            return borrowMessage("客户端", hasText(owner.getResourceName())
                    ? owner.getResourceName() : owner.getResourceId());
        }
        return null;
    }

    @Override
    public String checkModels(Collection<String> modelIds, String ownerId) {
        if (modelIds == null || modelIds.isEmpty()) {
            return null;
        }
        List<AiResourceOwnerVO> owners = agentRepository.queryModelOwners(new ArrayList<>(modelIds));
        for (AiResourceOwnerVO owner : owners) {
            if (isOwnedBy(owner.getOwnerId(), ownerId)) {
                continue;
            }
            return borrowMessage("模型", hasText(owner.getResourceName())
                    ? owner.getResourceName() : owner.getResourceId());
        }
        return null;
    }

    @Override
    public List<AiAgentClientFlowConfigVO> queryUserOwnFlowConfigs(String agentId, String userId) {
        if (!hasText(agentId) || !hasText(userId)) {
            return List.of();
        }
        return agentRepository.queryUserOwnFlowConfigs(agentId, userId);
    }

    @Override
    public boolean canWriteApiChannel(String apiId, String userId, boolean admin) {
        if (!hasText(apiId)) {
            return false;
        }
        AiResourceOwnerVO owner = agentRepository.queryApiChannelOwner(apiId);
        // 通道不存在同样视为无权（apiId 为空/悬空时绝不能放行装配）
        return owner != null && OwnerScope.canWrite(owner.getOwnerId(), userId, admin);
    }

    /**
     * 校验「已落库的智能体链路」：装配关系 → 客户端 → 模型，是否都属于 ownerId。
     * <p>
     * 只在"这个智能体是他自己建的"时调用；平台默认智能体（owner 为空）不校验。
     */
    private String checkAgentChain(String agentId, String ownerId) {
        if (!hasText(agentId) || !hasText(ownerId)) {
            return null;
        }

        // 智能体启用中的流程配置（status=1）→ clientIds
        List<AiAgentClientFlowConfigVO> flowConfigs = agentRepository.queryAiAgentClientsByAgentId(agentId);
        List<String> clientIds = new ArrayList<>();
        for (AiAgentClientFlowConfigVO flowConfig : flowConfigs) {
            if (hasText(flowConfig.getClientId())) {
                clientIds.add(flowConfig.getClientId());
            }
        }

        String message = checkClients(clientIds, ownerId);
        if (message != null) {
            return message;
        }

        // 客户端上挂的模型：ai_client_config 里 source=client、target=model
        return checkModels(agentRepository.queryModelIdsByClientIds(clientIds), ownerId);
    }

    /**
     * 「平台默认智能体必须绑了自己的 Key」校验。
     * <p>
     * 绑定关系存在 {@code ai_agent_flow_config}（agent_id + client_id + owner_id = 他）：
     * 有归他的那一条 = 绑过；没有 = 没绑，拦下并引导他去绑。
     */
    private String checkBindingRequired(String agentId, String ownerId) {
        if (!hasText(agentId) || !hasText(ownerId)) {
            return null;
        }
        if (!agentRepository.queryUserOwnFlowConfigs(agentId, ownerId).isEmpty()) {
            return null;
        }
        return "该智能体还没有绑定你自己的模型 Key：平台默认 Key 只给管理员使用，"
                + "请到「客户端 API 管理」配置你的 base_url 与 API Key 并绑定这个智能体";
    }

    /** 归属必须**正好是他的**：公共（owner 为空）与别人的都算借道 */
    private boolean isOwnedBy(String ownerId, String currentUserId) {
        return hasText(ownerId) && ownerId.equals(currentUserId);
    }

    private String borrowMessage(String type, String name) {
        return "引用的" + type + "「" + name + "」不是你自己的资源：普通用户自建智能体必须使用自己的模型，"
                + GUIDE + "（平台默认资源只能由管理员维护）";
    }

    private boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

}
