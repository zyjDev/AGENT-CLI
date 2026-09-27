package cn.bugstack.ai.trigger.support;

import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.infrastructure.dao.IAiAgentFlowConfigDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientConfigDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientDao;
import cn.bugstack.ai.infrastructure.dao.IAiClientModelDao;
import cn.bugstack.ai.infrastructure.dao.po.AiAgentFlowConfig;
import cn.bugstack.ai.infrastructure.dao.po.AiClient;
import cn.bugstack.ai.infrastructure.dao.po.AiClientConfig;
import cn.bugstack.ai.infrastructure.dao.po.AiClientModel;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 「普通用户自建智能体必须使用自己的模型 Key」守卫。
 *
 * ============================ 规则（2026-09-27 与业务确认）============================
 * 1. <b>管理员</b>不受限：他维护平台默认链路（owner_id 为空 = 人人可用）。
 * 2. <b>普通用户自己建的</b>智能体，链路上的「客户端 / 模型」必须**都是他自己的** ——
 *    owner_id 为空（平台默认）或属于别人，都算"借道"，一律拦下。
 * 3. <b>平台默认智能体</b>不受此限：普通用户本来就被允许用平台 Key 跑默认智能体。
 *
 * 为什么必须拦：模型这一层直接对应 {@code ai_client_api} 里的 base_url + api_key，
 * 借道公共模型 = 消耗管理员的额度。产品规则是"自建就必须配自己的 Key"，所以要在
 * 保存编排 / 装配 / 对话三处都拦住，而不是只在界面上做个提示。
 * ==============================================================================
 *
 * @author bugstack虫洞栈
 */
@Component
public class OwnModelGuard {

    /** 提示里必须告诉用户去哪儿配，否则报错等于没给出口 */
    private static final String GUIDE = "请到「客户端 API 管理」配置你自己的 base_url 与 API Key";

    @Resource
    private IAiAgentFlowConfigDao aiAgentFlowConfigDao;
    @Resource
    private IAiClientDao aiClientDao;
    @Resource
    private IAiClientConfigDao aiClientConfigDao;
    @Resource
    private IAiClientModelDao aiClientModelDao;

    /**
     * 校验「已落库的智能体链路」：装配关系 → 客户端 → 模型，是否都属于 ownerId。
     * <p>
     * 只在"这个智能体是他自己建的"时调用；平台默认智能体（owner 为空）不校验。
     *
     * @param agentId 智能体 id
     * @param ownerId 期望的归属（普通用户自己的 userId）
     * @return null = 通过；否则返回给用户看的提示
     */
    public String checkAgentChain(String agentId, String ownerId) {
        if (!StringUtils.hasText(agentId) || !StringUtils.hasText(ownerId)) {
            return null;
        }

        List<AiAgentFlowConfig> flowConfigs = aiAgentFlowConfigDao.queryEnabledByAgentId(agentId);
        List<String> clientIds = new ArrayList<>();
        for (AiAgentFlowConfig flowConfig : flowConfigs) {
            if (StringUtils.hasText(flowConfig.getClientId())) {
                clientIds.add(flowConfig.getClientId());
            }
        }

        String message = checkClients(clientIds, ownerId);
        if (message != null) {
            return message;
        }

        // 客户端上挂的模型：ai_client_config 里 source=client、target=model
        List<String> modelIds = new ArrayList<>();
        for (String clientId : clientIds) {
            List<AiClientConfig> relations =
                    aiClientConfigDao.queryBySourceTypeAndId(AiAgentEnumVO.AI_CLIENT.getCode(), clientId);
            for (AiClientConfig relation : relations) {
                if (AiAgentEnumVO.AI_CLIENT_MODEL.getCode().equals(relation.getTargetType())
                        && StringUtils.hasText(relation.getTargetId())) {
                    modelIds.add(relation.getTargetId());
                }
            }
        }
        return checkModels(modelIds, ownerId);
    }

    /**
     * 逐条校验客户端归属。
     *
     * @return null = 通过；否则返回给用户看的提示
     */
    public String checkClients(Collection<String> clientIds, String ownerId) {
        if (clientIds == null) {
            return null;
        }
        for (String clientId : clientIds) {
            AiClient client = aiClientDao.queryByClientId(clientId);
            if (client == null) {
                // 引用不存在交给原有的"配置不完整"提示，这里不抢它的活
                continue;
            }
            if (isOwnedBy(client.getOwnerId(), ownerId)) {
                continue;
            }
            return borrowMessage("客户端", StringUtils.hasText(client.getClientName())
                    ? client.getClientName() : clientId);
        }
        return null;
    }

    /**
     * 逐条校验模型归属。
     *
     * @return null = 通过；否则返回给用户看的提示
     */
    public String checkModels(Collection<String> modelIds, String ownerId) {
        if (modelIds == null) {
            return null;
        }
        for (String modelId : modelIds) {
            AiClientModel model = aiClientModelDao.queryByModelId(modelId);
            if (model == null) {
                continue;
            }
            if (isOwnedBy(model.getOwnerId(), ownerId)) {
                continue;
            }
            return borrowMessage("模型", StringUtils.hasText(model.getModelName())
                    ? model.getModelName() : modelId);
        }
        return null;
    }

    /** 归属必须**正好是他的**：公共（owner 为空）与别人的都算借道 */
    private boolean isOwnedBy(String ownerId, String currentUserId) {
        return StringUtils.hasText(ownerId) && ownerId.equals(currentUserId);
    }

    private String borrowMessage(String type, String name) {
        return "引用的" + type + "「" + name + "」不是你自己的资源：普通用户自建智能体必须使用自己的模型，"
                + GUIDE + "（平台默认资源只能由管理员维护）";
    }
}
