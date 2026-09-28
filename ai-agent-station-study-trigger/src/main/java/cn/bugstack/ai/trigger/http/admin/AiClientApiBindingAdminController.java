package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiClientApiBindingAdminService;
import cn.bugstack.ai.api.dto.AiClientApiBindRequestDTO;
import cn.bugstack.ai.api.dto.AiClientApiBoundAgentResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import cn.bugstack.ai.infrastructure.dao.*;
import cn.bugstack.ai.infrastructure.dao.po.*;
import cn.bugstack.ai.trigger.support.OwnerGuard;
import cn.bugstack.ai.types.common.OwnerScope;
import cn.bugstack.ai.types.common.SnowflakeId;
import cn.bugstack.ai.types.context.UserContext;
import cn.bugstack.ai.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「用户自己的模型 Key ↔ 智能体」绑定：把普通用户配的 base_url + api_key 接到指定智能体上。
 *
 * ============================ 为什么需要它 ============================
 * 平台默认 Key（管理员的）只给管理员用。普通用户要用某个智能体 —— 包括那 6 个基础智能体 ——
 * 必须先配好自己的 Key 并绑定给它，否则对话/装配会被 {@code AgentAccessService} 拦下。
 *
 * 绑定关系<b>不引入新表</b>，就落在既有的 {@code ai_agent_flow_config}（agent_id + client_id + owner_id）：
 * <ol>
 *   <li>用户不必懂「API → 模型 → 客户端 → 装配关系」四层，这里替他接线，且全部归他本人；</li>
 *   <li>运行时按 (agentId, userId) 优先取他自己的那份流程配置，没有才回落系统默认
 *       （见 {@code IAiAgentFlowConfigDao.queryEnabledByAgentIdPreferOwner}）；</li>
 *   <li>同一智能体重复绑定 = 覆盖；解绑 = 删掉他那几条绑定行。</li>
 * </ol>
 *
 * ⚠️ 这里<b>刻意不加 @Transactional</b>：装配用的是独立线程池（CompletableFuture），
 * 那些线程看不到未提交的数据 —— 若在事务里装配，装配线程读到的还是"绑定之前"的链路，
 * 等于白装。因此改为顺序写入（各自提交）后再装配；写入顺序也刻意把「绑定行」放在最后，
 * 中途失败只会留下未完成的模型/客户端行，不会留下"半个绑定"。
 * =====================================================================
 *
 * @author bugstack虫洞栈
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-api")
public class AiClientApiBindingAdminController implements IAiClientApiBindingAdminService {

    /** 自动接线的客户端名后缀：让用户在客户端列表里一眼看出这条链路是"我的 Key" */
    private static final String MY_KEY_CLIENT_SUFFIX = "（我的Key）";
    /** ai_client.client_name 是 varchar(50)，拼接后要截断再存 */
    private static final int CLIENT_NAME_MAX_LENGTH = 50;

    @Resource
    private IAiClientApiDao aiClientApiDao;
    @Resource
    private IAiClientModelDao aiClientModelDao;
    @Resource
    private IAiClientDao aiClientDao;
    @Resource
    private IAiClientConfigDao aiClientConfigDao;
    @Resource
    private IAiAgentFlowConfigDao aiAgentFlowConfigDao;
    @Resource
    private IAiAgentDao aiAgentDao;
    @Resource
    private IArmoryService armoryService;

    @Override
    @PostMapping("/bind-agent")
    public Response<Boolean> bindAgent(@RequestBody AiClientApiBindRequestDTO request) {
        try {
            // 1. 身份与参数
            String userId = UserContext.userId();
            if (!StringUtils.hasText(userId)) {
                return bindFail("未登录或登录已过期");
            }
            if (request == null || !StringUtils.hasText(request.getApiId()) || !StringUtils.hasText(request.getAgentId())) {
                return bindFail("apiId 与 agentId 不能为空");
            }

            // 2. 只能绑自己的密钥：公共 API 行是管理员的平台密钥，普通用户把它挂到自己名下等于白嫖平台额度
            AiClientApi api = aiClientApiDao.queryByApiId(request.getApiId());
            if (api == null || !OwnerGuard.writable(api.getOwnerId())) {
                return OwnerGuard.deny("API 通道");
            }
            if (!userId.equals(api.getOwnerId())) {
                return bindFail("只能绑定你自己的 API 密钥：平台默认密钥由管理员维护");
            }
            // 管理员无需绑定：平台默认链路本来就是他的 Key，而且他新建资源会落成"公共"，
            // 一旦在这里接线，反而会把他的私有 Key 写进公共链路、影响所有用户
            if (UserContext.isAdmin()) {
                return bindFail("管理员无需绑定：平台默认智能体走的就是你的密钥");
            }

            // 3. 智能体必须对他可见（系统默认 or 他自己的）
            AiAgent agent = aiAgentDao.queryByAgentId(request.getAgentId());
            if (agent == null || !OwnerScope.isVisible(agent.getOwnerId(), userId)) {
                return bindFail("智能体不存在或无权访问");
            }

            // 4. 解析该智能体现有链路（纯查询，全部校验放在任何写库之前）
            List<AiAgentFlowConfig> existing = aiAgentFlowConfigDao.queryEnabledByAgentId(request.getAgentId());
            if (existing.isEmpty()) {
                return bindFail("该智能体还没有可用的客户端配置，无法绑定");
            }
            List<AiClientModel> sourceModels = new ArrayList<>();
            for (AiAgentFlowConfig flowConfig : existing) {
                AiClientModel source = findModelByClientId(flowConfig.getClientId());
                if (source == null) {
                    return bindFail("智能体链路不完整：客户端 " + flowConfig.getClientId() + " 没有挂载模型");
                }
                sourceModels.add(source);
            }

            // 5. 接线（全部归他）：沿用原链路的「模型名」，只把 base_url / api_key 换成他的
            List<AiClient> hisClients = new ArrayList<>();
            for (AiClientModel source : sourceModels) {
                String hisModelId = ensureHisModel(userId, api.getApiId(), source);
                hisClients.add(ensureHisClient(userId, hisModelId, agent.getAgentName()));
            }

            // 6. 写绑定行（最后一步）：先删他在这条智能体上的旧绑定，再按现有链路逐个 clientType 重建
            aiAgentFlowConfigDao.deleteByAgentIdAndOwner(request.getAgentId(), userId);
            for (int i = 0; i < existing.size(); i++) {
                AiAgentFlowConfig origin = existing.get(i);
                AiClient hisClient = hisClients.get(i);
                AiAgentFlowConfig binding = new AiAgentFlowConfig();
                binding.setAgentId(request.getAgentId());
                binding.setClientId(hisClient.getClientId());
                binding.setClientName(hisClient.getClientName());
                binding.setClientType(origin.getClientType());
                binding.setSequence(origin.getSequence());
                binding.setStepPrompt(origin.getStepPrompt());
                binding.setStatus(1);
                binding.setCreateTime(LocalDateTime.now());
                // 归属：这条绑定必须归他自己，运行期才会优先取它
                OwnerGuard.stampOwnerOnCreate(binding);
                aiAgentFlowConfigDao.insert(binding);
            }

            // 7. 立即装配（失败不影响绑定结果：运行期首次对话还会自动补装）
            assembleQuietly(request.getAgentId());
            log.info("绑定自己的 Key 成功：userId={}, apiId={}, agentId={}, 绑定条目={}",
                    userId, api.getApiId(), request.getAgentId(), existing.size());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("绑定成功，该智能体将使用你的模型 Key")
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("绑定自己的 Key 失败，agentId={}", request == null ? null : request.getAgentId(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PostMapping("/unbind-agent")
    public Response<Boolean> unbindAgent(@RequestBody AiClientApiBindRequestDTO request) {
        try {
            String userId = UserContext.userId();
            if (!StringUtils.hasText(userId)) {
                return bindFail("未登录或登录已过期");
            }
            if (request == null || !StringUtils.hasText(request.getAgentId())) {
                return bindFail("agentId 不能为空");
            }
            AiAgent agent = aiAgentDao.queryByAgentId(request.getAgentId());
            if (agent == null || !OwnerScope.isVisible(agent.getOwnerId(), userId)) {
                return bindFail("智能体不存在或无权访问");
            }

            // 只删他的那几条绑定行：他的 model / client 行保留（可能被他别的智能体共用）
            int removed = aiAgentFlowConfigDao.deleteByAgentIdAndOwner(request.getAgentId(), userId);
            if (removed <= 0) {
                return bindFail("该智能体没有绑定你的 Key，无需解绑");
            }
            log.info("解绑成功：userId={}, agentId={}, 删除绑定条目={}", userId, request.getAgentId(), removed);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("已解绑：该智能体对你不再可用，需要重新绑定你的 Key")
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("解绑失败，agentId={}", request == null ? null : request.getAgentId(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PostMapping("/bound-agents")
    public Response<List<AiClientApiBoundAgentResponseDTO>> queryBoundAgents(@RequestBody AiClientApiBindRequestDTO request) {
        try {
            String userId = UserContext.userId();
            if (!StringUtils.hasText(userId)) {
                return boundFail("未登录或登录已过期");
            }
            if (request == null || !StringUtils.hasText(request.getApiId())) {
                return boundFail("apiId 不能为空");
            }
            AiClientApi api = aiClientApiDao.queryByApiId(request.getApiId());
            if (api == null || !OwnerGuard.writable(api.getOwnerId())) {
                return boundFail("API 通道不存在或无权访问");
            }

            // 反查绑定：他的 API → 挂在该 API 下的模型 → ai_client_config(target=model) → 客户端 → 他在这条客户端上的绑定行 → 智能体
            Map<String, AiClientApiBoundAgentResponseDTO> bound = new LinkedHashMap<>();
            for (AiClientModel model : aiClientModelDao.queryByApiId(request.getApiId())) {
                for (AiClientConfig relation : aiClientConfigDao.queryByTargetId(model.getModelId())) {
                    if (!AiAgentEnumVO.AI_CLIENT_MODEL.getCode().equals(relation.getTargetType())) {
                        continue;
                    }
                    AiClient client = aiClientDao.queryByClientId(relation.getSourceId());
                    if (client == null || !userId.equals(client.getOwnerId())) {
                        continue;
                    }
                    for (AiAgentFlowConfig binding : aiAgentFlowConfigDao.queryByClientId(client.getClientId())) {
                        if (!userId.equals(binding.getOwnerId())
                                || binding.getStatus() == null || binding.getStatus() != 1
                                || bound.containsKey(binding.getAgentId())) {
                            continue;
                        }
                        AiAgent agent = aiAgentDao.queryByAgentId(binding.getAgentId());
                        bound.put(binding.getAgentId(), AiClientApiBoundAgentResponseDTO.builder()
                                .agentId(binding.getAgentId())
                                .agentName(agent == null ? binding.getAgentId() : agent.getAgentName())
                                .platformDefault(agent != null && agent.getOwnerId() == null)
                                .build());
                    }
                }
            }

            return Response.<List<AiClientApiBoundAgentResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(new ArrayList<>(bound.values()))
                    .build();
        } catch (Exception e) {
            log.error("查询已绑定智能体失败，apiId={}", request == null ? null : request.getApiId(), e);
            return boundFail(ResponseCode.UN_ERROR.getInfo());
        }
    }

    /**
     * 保证「他的模型行」存在并返回 modelId：同一条 API 下已有同名模型就直接复用，避免每次绑定都堆一行。
     */
    private String ensureHisModel(String userId, String apiId, AiClientModel source) {
        for (AiClientModel model : aiClientModelDao.queryByApiId(apiId)) {
            if (userId.equals(model.getOwnerId()) && source.getModelName().equals(model.getModelName())) {
                return model.getModelId();
            }
        }
        AiClientModel hisModel = new AiClientModel();
        hisModel.setModelId(SnowflakeId.nextIdStr());
        hisModel.setApiId(apiId);
        hisModel.setModelName(source.getModelName());
        hisModel.setModelType(source.getModelType());
        hisModel.setModelUsage(source.getModelUsage());
        hisModel.setStatus(1);
        hisModel.setCreateTime(LocalDateTime.now());
        hisModel.setUpdateTime(LocalDateTime.now());
        OwnerGuard.stampOwnerOnCreate(hisModel);
        aiClientModelDao.insert(hisModel);
        log.info("为用户接线模型行：userId={}, modelId={}, modelName={}", userId, hisModel.getModelId(), hisModel.getModelName());
        return hisModel.getModelId();
    }

    /**
     * 保证「他的客户端行 + 客户端→模型的关系行」存在并返回客户端。
     * 同一模型下已有他自己的客户端就复用（一个模型一行客户端，不会每次绑定都堆）。
     */
    private AiClient ensureHisClient(String userId, String hisModelId, String agentName) {
        for (AiClientConfig relation : aiClientConfigDao.queryByTargetId(hisModelId)) {
            if (!AiAgentEnumVO.AI_CLIENT_MODEL.getCode().equals(relation.getTargetType())) {
                continue;
            }
            AiClient existed = aiClientDao.queryByClientId(relation.getSourceId());
            if (existed != null && userId.equals(existed.getOwnerId())) {
                return existed;
            }
        }

        String clientName = buildClientName(agentName);
        AiClient hisClient = new AiClient();
        hisClient.setClientId(SnowflakeId.nextIdStr());
        hisClient.setClientName(clientName);
        hisClient.setStatus(1);
        hisClient.setCreateTime(LocalDateTime.now());
        hisClient.setUpdateTime(LocalDateTime.now());
        OwnerGuard.stampOwnerOnCreate(hisClient);
        aiClientDao.insert(hisClient);

        AiClientConfig relation = new AiClientConfig();
        relation.setSourceType(AiAgentEnumVO.AI_CLIENT.getCode());
        relation.setSourceId(hisClient.getClientId());
        relation.setTargetType(AiAgentEnumVO.AI_CLIENT_MODEL.getCode());
        relation.setTargetId(hisModelId);
        relation.setStatus(1);
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        OwnerGuard.stampOwnerOnCreate(relation);
        aiClientConfigDao.insert(relation);

        log.info("为用户接线客户端行：userId={}, clientId={}, clientName={}", userId, hisClient.getClientId(), clientName);
        return hisClient;
    }

    /** 客户端名：智能体名 + 后缀，超出列长度就截断（varchar(50)） */
    private String buildClientName(String agentName) {
        String base = StringUtils.hasText(agentName) ? agentName.trim() : "我的智能体";
        String name = base + MY_KEY_CLIENT_SUFFIX;
        return name.length() <= CLIENT_NAME_MAX_LENGTH ? name : name.substring(0, CLIENT_NAME_MAX_LENGTH);
    }

    /** 顺着客户端找它挂的模型（ai_client_config：source=client → target=model） */
    private AiClientModel findModelByClientId(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return null;
        }
        for (AiClientConfig relation : aiClientConfigDao.queryBySourceTypeAndId(AiAgentEnumVO.AI_CLIENT.getCode(), clientId)) {
            if (!AiAgentEnumVO.AI_CLIENT_MODEL.getCode().equals(relation.getTargetType())) {
                continue;
            }
            AiClientModel model = aiClientModelDao.queryByModelId(relation.getTargetId());
            if (model != null) {
                return model;
            }
        }
        return null;
    }

    /**
     * 装配该智能体（把他的链路注册成 Bean）。失败只记日志：
     * 绑定数据已经写好了，运行期首次对话还会自动补装，不该因为装配失败把绑定结果也判失败。
     */
    private void assembleQuietly(String agentId) {
        try {
            armoryService.acceptArmoryAgent(agentId);
        } catch (Exception e) {
            log.warn("绑定后立即装配失败（不影响绑定结果，运行期会自动补装）：agentId={}", agentId, e);
        }
    }

    private Response<Boolean> bindFail(String info) {
        return Response.<Boolean>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(info)
                .data(false)
                .build();
    }

    private Response<List<AiClientApiBoundAgentResponseDTO>> boundFail(String info) {
        return Response.<List<AiClientApiBoundAgentResponseDTO>>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(info)
                .data(null)
                .build();
    }

}
