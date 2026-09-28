package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.valobj.*;
import cn.bugstack.ai.infrastructure.dao.*;
import cn.bugstack.ai.infrastructure.dao.po.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO.*;

/**
 * AiAgent 仓储服务
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/6/28 18:09
 */
@Slf4j
@Repository
public class AgentRepository implements IAgentRepository {

    @Resource
    private IAiAgentDao aiAgentDao;

    @Resource
    private IAiAgentFlowConfigDao aiAgentFlowConfigDao;

    @Resource
    private IAiAgentTaskScheduleDao aiAgentTaskScheduleDao;

    @Resource
    private IAiClientAdvisorDao aiClientAdvisorDao;

    @Resource
    private IAiClientApiDao aiClientApiDao;

    @Resource
    private IAiClientConfigDao aiClientConfigDao;

    @Resource
    private IAiClientDao aiClientDao;

    @Resource
    private IAiClientModelDao aiClientModelDao;

    @Resource
    private IAiClientRagOrderDao aiClientRagOrderDao;

    @Resource
    private IAiClientSystemPromptDao aiClientSystemPromptDao;

    @Resource
    private IAiClientToolMcpDao aiClientToolMcpDao;

    @Override
    public List<AiClientApiVO> queryAiClientApiVOListByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientApiVO> result = new ArrayList<>();

        for (String clientId : clientIdList) {
            // 1. 通过clientId查询关联的modelId
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && isEnabled(config.getStatus())) {
                    String modelId = config.getTargetId();

                    // 2. 通过modelId查询模型配置，获取apiId
                    AiClientModel model = aiClientModelDao.queryByModelId(modelId);
                    if (model != null && isEnabled(model.getStatus())) {
                        String apiId = model.getApiId();

                        // 3. 通过apiId查询API配置信息
                        AiClientApi apiConfig = aiClientApiDao.queryByApiId(apiId);
                        if (apiConfig != null && isEnabled(apiConfig.getStatus())) {
                            // 4. 转换为VO对象
                            AiClientApiVO apiVO = AiClientApiVO.builder()
                                    .apiId(apiConfig.getApiId())
                                    .baseUrl(apiConfig.getBaseUrl())
                                    .apiKey(apiConfig.getApiKey())
                                    .completionsPath(apiConfig.getCompletionsPath())
                                    .embeddingsPath(apiConfig.getEmbeddingsPath())
                                    .build();

                            // 避免重复添加相同的API配置
                            if (result.stream().noneMatch(vo -> vo.getApiId().equals(apiVO.getApiId()))) {
                                result.add(apiVO);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<AiClientModelVO> AiClientModelVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientModelVO> result = new ArrayList<>();

        for (String clientId : clientIdList) {
            // 1. 通过clientId查询关联的modelId
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && isEnabled(config.getStatus())) {
                    String modelId = config.getTargetId();

                    // 2. 通过modelId查询模型配置
                    AiClientModel model = aiClientModelDao.queryByModelId(modelId);
                    if (model != null && isEnabled(model.getStatus())) {

                        // 3. 查询该模型关联的tool_mcp配置
                        List<AiClientConfig> toolMcpConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                        List<String> toolMcpIds = new ArrayList<>();
                        for (AiClientConfig toolMcpConfig : toolMcpConfigs) {
                            if (AI_CLIENT_TOOL_MCP.getCode().equals(toolMcpConfig.getTargetType()) && isEnabled(toolMcpConfig.getStatus())) {
                                toolMcpIds.add(toolMcpConfig.getTargetId());
                            }
                        }

                        // 4. 构建模型VO
                        AiClientModelVO modelVO = AiClientModelVO.builder()
                                .modelId(model.getModelId())
                                .modelName(model.getModelName())
                                .apiId(model.getApiId())
                                .modelType(model.getModelType())
                                // 此处曾装配 .typeName(model.getTypeName())：该字段非数据库列
                                // （库中无 type_name 列、MP 不生成 SELECT 列、取值恒为 null），
                                // 属无效赋值，已随该死字段一并移除。
                                .status(model.getStatus())
                                .build();

                        result.add(modelVO);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<AiClientToolMcpVO> AiClientToolMcpVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientToolMcpVO> result = new ArrayList<>();

        for (String clientId : clientIdList) {
            // 1. 通过clientId查询关联的modelId
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && isEnabled(config.getStatus())) {
                    String modelId = config.getTargetId();

                    // 2. 查询该模型关联的tool_mcp配置
                    List<AiClientConfig> toolMcpConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                    for (AiClientConfig toolMcpConfig : toolMcpConfigs) {
                        if (AI_CLIENT_TOOL_MCP.getCode().equals(toolMcpConfig.getTargetType()) && isEnabled(toolMcpConfig.getStatus())) {
                            String toolMcpId = toolMcpConfig.getTargetId();

                            // 3. 查询tool_mcp配置信息
                            AiClientToolMcp toolMcp = aiClientToolMcpDao.queryByMcpId(toolMcpId);
                            if (toolMcp != null && isEnabled(toolMcp.getStatus())) {
                                AiClientToolMcpVO toolMcpVO = AiClientToolMcpVO.builder()
                                        .toolMcpId(toolMcp.getMcpId())
                                        .toolMcpName(toolMcp.getMcpName())
                                        .transportType(toolMcp.getTransportType())
                                        .build();

                                result.add(toolMcpVO);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<AiClientSystemPromptVO> AiClientSystemPromptVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientSystemPromptVO> result = new ArrayList<>();

        for (String clientId : clientIdList) {
            // 1. 通过clientId查询关联的配置
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            // 收集所有需要查询的promptId
            Set<String> promptIds = new HashSet<>();

            for (AiClientConfig config : configs) {
                if (!isEnabled(config.getStatus())) continue;
                // 1a. 直接挂在client上的prompt
                if (AI_CLIENT_SYSTEM_PROMPT.getCode().equals(config.getTargetType())) {
                    promptIds.add(config.getTargetId());
                }
                // 1b. 通过client -> model -> prompt链路查找
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType())) {
                    String modelId = config.getTargetId();
                    List<AiClientConfig> promptConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                    for (AiClientConfig promptConfig : promptConfigs) {
                        if (AI_CLIENT_SYSTEM_PROMPT.getCode().equals(promptConfig.getTargetType()) && isEnabled(promptConfig.getStatus())) {
                            promptIds.add(promptConfig.getTargetId());
                        }
                    }
                }
            }

            // 2. 批量查询prompt配置
            for (String promptId : promptIds) {
                AiClientSystemPrompt prompt = aiClientSystemPromptDao.queryByPromptId(promptId);
                if (prompt != null && isEnabled(prompt.getStatus())) {
                    AiClientSystemPromptVO promptVO = AiClientSystemPromptVO.builder()
                            .promptId(prompt.getPromptId())
                            .promptName(prompt.getPromptName())
                            .promptContent(prompt.getPromptContent())
                            .description(prompt.getDescription())
                            .status(prompt.getStatus())
                            .build();
                    result.add(promptVO);
                }
            }
        }

        return result;
    }

    @Override
    public Map<String, AiClientSystemPromptVO> queryAiClientSystemPromptMapByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return Map.of();
        }

        Map<String, AiClientSystemPromptVO> result = new HashMap<>();

        for (String clientId : clientIdList) {
            // 1. 通过clientId查询关联的配置
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            // 收集所有需要查询的promptId
            Set<String> promptIds = new HashSet<>();

            for (AiClientConfig config : configs) {
                if (!isEnabled(config.getStatus())) continue;
                // 1a. 直接挂在client上的prompt
                if (AI_CLIENT_SYSTEM_PROMPT.getCode().equals(config.getTargetType())) {
                    promptIds.add(config.getTargetId());
                }
                // 1b. 通过client -> model -> prompt链路查找
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType())) {
                    String modelId = config.getTargetId();
                    List<AiClientConfig> promptConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                    for (AiClientConfig promptConfig : promptConfigs) {
                        if (AI_CLIENT_SYSTEM_PROMPT.getCode().equals(promptConfig.getTargetType()) && isEnabled(promptConfig.getStatus())) {
                            promptIds.add(promptConfig.getTargetId());
                        }
                    }
                }
            }

            // 2. 批量查询prompt配置
            for (String promptId : promptIds) {
                AiClientSystemPrompt prompt = aiClientSystemPromptDao.queryByPromptId(promptId);
                if (prompt != null && isEnabled(prompt.getStatus())) {
                    AiClientSystemPromptVO promptVO = AiClientSystemPromptVO.builder()
                            .promptId(prompt.getPromptId())
                            .promptName(prompt.getPromptName())
                            .promptContent(prompt.getPromptContent())
                            .description(prompt.getDescription())
                            .status(prompt.getStatus())
                            .build();
                    result.put(promptId, promptVO);
                }
            }
        }

        return result;
    }

    @Override
    public List<AiClientAdvisorVO> AiClientAdvisorVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientAdvisorVO> result = new ArrayList<>();

        for (String clientId : clientIdList) {
            // 1. 通过clientId查询关联的advisor配置
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_ADVISOR.getCode().equals(config.getTargetType()) && isEnabled(config.getStatus())) {
                    String advisorId = config.getTargetId();

                    // 2. 查询advisor配置信息
                    AiClientAdvisor advisor = aiClientAdvisorDao.queryByAdvisorId(advisorId);
                    if (advisor != null && isEnabled(advisor.getStatus())) {
                        // 3. 解析extParam中的配置
                        AiClientAdvisorVO.ChatMemory chatMemory = null;
                        AiClientAdvisorVO.RagAnswer ragAnswer = null;

                        String extParam = advisor.getExtParam();
                        if (extParam != null && !extParam.trim().isEmpty()) {
                            try {
                                if ("ChatMemory".equals(advisor.getAdvisorType())) {
                                    // 解析chatMemory配置
                                    chatMemory = JSON.parseObject(extParam, AiClientAdvisorVO.ChatMemory.class);
                                } else if ("RagAnswer".equals(advisor.getAdvisorType())) {
                                    // 解析ragAnswer配置
                                    ragAnswer = JSON.parseObject(extParam, AiClientAdvisorVO.RagAnswer.class);
                                }
                            } catch (Exception e) {
                                // 解析失败时忽略，使用默认值null
                            }
                        }

                        AiClientAdvisorVO advisorVO = AiClientAdvisorVO.builder()
                                .advisorId(advisor.getAdvisorId())
                                .advisorName(advisor.getAdvisorName())
                                .advisorType(advisor.getAdvisorType())
                                .orderNum(advisor.getOrderNum())
                                .chatMemory(chatMemory)
                                .ragAnswer(ragAnswer)
                                .build();

                        result.add(advisorVO);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<AiClientVO> AiClientVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientVO> result = new ArrayList<>();

        for (String clientId : clientIdList) {
            AiClient aiClient = aiClientDao.queryByClientId(clientId);
            if (aiClient != null && isEnabled(aiClient.getStatus())) {
                // 查询 ai_client_config 获取关联的 model/prompt/mcp/advisor
                List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

                String modelId = null;
                List<String> promptIdList = new ArrayList<>();
                List<String> mcpIdList = new ArrayList<>();
                List<String> advisorIdList = new ArrayList<>();

                for (AiClientConfig config : configs) {
                    if (!isEnabled(config.getStatus())) continue;
                    switch (config.getTargetType()) {
                        case "model" -> modelId = config.getTargetId();
                        case "prompt" -> promptIdList.add(config.getTargetId());
                        case "tool_mcp" -> mcpIdList.add(config.getTargetId());
                        case "advisor" -> advisorIdList.add(config.getTargetId());
                    }
                }

                AiClientVO clientVO = AiClientVO.builder()
                        .clientId(aiClient.getClientId())
                        .clientName(aiClient.getClientName())
                        // 此处曾装配 .clientDesc(aiClient.getClientDesc())：该字段非数据库列
                        // （读取恒为 null），真实列是 description，已对齐真实列；无效字段亦已删除。
                        .description(aiClient.getDescription())
                        .status(aiClient.getStatus())
                        .modelId(modelId)
                        .promptIdList(promptIdList)
                        .mcpIdList(mcpIdList)
                        .advisorIdList(advisorIdList)
                        .build();

                result.add(clientVO);
            }
        }

        return result;
    }

    @Override
    public List<AiClientApiVO> queryAiClientApiVOListByModelIds(List<String> modelIdList) {
        if (modelIdList == null || modelIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientApiVO> result = new ArrayList<>();

        for (String modelId : modelIdList) {
            // 1. 通过modelId查询模型配置，获取apiId
            AiClientModel model = aiClientModelDao.queryByModelId(modelId);
            if (model != null && isEnabled(model.getStatus())) {
                String apiId = model.getApiId();

                // 2. 通过apiId查询API配置信息
                AiClientApi apiConfig = aiClientApiDao.queryByApiId(apiId);
                if (apiConfig != null && isEnabled(apiConfig.getStatus())) {
                    AiClientApiVO apiVO = AiClientApiVO.builder()
                            .apiId(apiConfig.getApiId())
                            .baseUrl(apiConfig.getBaseUrl())
                            .apiKey(apiConfig.getApiKey())
                            .completionsPath(apiConfig.getCompletionsPath())
                            .embeddingsPath(apiConfig.getEmbeddingsPath())
                            .build();

                    if (result.stream().noneMatch(vo -> vo.getApiId().equals(apiVO.getApiId()))) {
                        result.add(apiVO);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<AiClientModelVO> AiClientModelVOByModelIds(List<String> modelIdList) {
        if (modelIdList == null || modelIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientModelVO> result = new ArrayList<>();

        for (String modelId : modelIdList) {
            AiClientModel model = aiClientModelDao.queryByModelId(modelId);
            if (model != null && isEnabled(model.getStatus())) {
                AiClientModelVO modelVO = AiClientModelVO.builder()
                        .modelId(model.getModelId())
                        .modelName(model.getModelName())
                        .apiId(model.getApiId())
                        .modelType(model.getModelType())
                        // 原先此处有 .typeName(model.getTypeName())，取值恒为 null，已移除（同上）
                        .status(model.getStatus())
                        .build();

                result.add(modelVO);
            }
        }

        return result;
    }

    @Override
    public Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId) {
        // 不传 owner：等价于「系统默认链路」，用于定时任务等没有用户上下文的场景
        return queryAiAgentClientFlowConfig(aiAgentId, null);
    }

    @Override
    public Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId, String ownerId) {
        try {
            // 只加载 status=1 的有效流程配置，避免已禁用的占位节点（如 agent_id='1' 的 2101~2103）参与执行。
            // 归属优先：用户绑定过自己的 Key 就走他的链路（否则一直用管理员的 Key，绑了等于白绑），没绑过回落系统默认
            List<AiAgentFlowConfig> flowConfigs = aiAgentFlowConfigDao.queryEnabledByAgentIdPreferOwner(aiAgentId, ownerId);
            Map<String, AiAgentClientFlowConfigVO> result = new HashMap<>();

            for (AiAgentFlowConfig flowConfig : flowConfigs) {
                AiAgentClientFlowConfigVO configVO = AiAgentClientFlowConfigVO.builder()
                        .clientId(flowConfig.getClientId())
                        .clientName(flowConfig.getClientName())
                        .clientType(flowConfig.getClientType())
                        .sequence(flowConfig.getSequence())
                        .stepPrompt(flowConfig.getStepPrompt())
                        .build();

                result.put(flowConfig.getClientType(), configVO);
            }

            return result;
        } catch (NumberFormatException e) {
            log.error("Invalid aiAgentId format: {}", aiAgentId, e);
            return Map.of();
        } catch (Exception e) {
            log.error("Query ai agent client flow config failed, aiAgentId: {}", aiAgentId, e);
            return Map.of();
        }
    }
    
    /**
     * 根据智能体ID查询智能体信息
     * @param aiAgentId 智能体ID
     * @return 智能体VO
     */
    @Override
    public AiAgentVO queryAiAgentByAgentId(String aiAgentId) {
        AiAgent aiAgent = aiAgentDao.queryByAgentId(aiAgentId);
        if (null == aiAgent) {
            log.warn("查询智能体信息为空，aiAgentId：{}", aiAgentId);
            return null;
        }

        return AiAgentVO.builder()
                .agentId(aiAgent.getAgentId())
                .agentName(aiAgent.getAgentName())
                .description(aiAgent.getDescription())
                .channel(aiAgent.getChannel())
                .strategy(aiAgent.getStrategy())
                .status(aiAgent.getStatus())
                // 归属校验（能否使用 / 是否"自建"）需要它，见 AgentAccessService
                .ownerId(aiAgent.getOwnerId())
                .build();
    }

    /**
     * 根据智能体ID查询智能体关联的客户端模型
     * @param aiAgentId 智能体ID
     * @return 客户端模型列表
     */
    @Override
    public List<AiAgentClientFlowConfigVO> queryAiAgentClientsByAgentId(String aiAgentId) {
        // 不传 owner：装配场景要把该智能体的**全部**流程配置都注册成 Bean
        // （系统默认那份 + 各用户绑定时写入的私有那份），运行期再按用户取用，见 ArmoryService
        return queryAiAgentClientsByAgentId(aiAgentId, null);
    }

    @Override
    public List<AiAgentClientFlowConfigVO> queryAiAgentClientsByAgentId(String aiAgentId, String ownerId) {
        List<AiAgentClientFlowConfigVO> aiAgentClientFlowConfigVOS = new ArrayList<>();

        // 只加载 status=1 的有效流程配置：本方法同时服务于 FixedAgentExecuteStrategy 执行与 Armory 装配
        List<AiAgentFlowConfig> flowConfigs = (ownerId == null || ownerId.isBlank())
                ? aiAgentFlowConfigDao.queryEnabledByAgentId(aiAgentId)
                : aiAgentFlowConfigDao.queryEnabledByAgentIdPreferOwner(aiAgentId, ownerId);
        for (AiAgentFlowConfig flowConfig : flowConfigs) {
            AiAgentClientFlowConfigVO configVO = AiAgentClientFlowConfigVO.builder()
                    .clientId(flowConfig.getClientId())
                    .clientName(flowConfig.getClientName())
                    .clientType(flowConfig.getClientType())
                    .sequence(flowConfig.getSequence())
                    .stepPrompt(flowConfig.getStepPrompt())
                    .build();
            aiAgentClientFlowConfigVOS.add(configVO);
        }

        return aiAgentClientFlowConfigVOS;
    }

    @Override
    public List<AiResourceOwnerVO> queryClientOwners(List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return List.of();
        }

        List<AiResourceOwnerVO> result = new ArrayList<>();
        for (String clientId : clientIds) {
            AiClient client = aiClientDao.queryByClientId(clientId);
            if (client == null) {
                // 引用不存在交给原有的「配置不完整」提示，归属校验不抢它的活
                continue;
            }
            result.add(AiResourceOwnerVO.builder()
                    .resourceId(client.getClientId())
                    .resourceName(client.getClientName())
                    .ownerId(client.getOwnerId())
                    .build());
        }
        return result;
    }

    @Override
    public List<AiResourceOwnerVO> queryModelOwners(List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return List.of();
        }

        List<AiResourceOwnerVO> result = new ArrayList<>();
        for (String modelId : modelIds) {
            AiClientModel model = aiClientModelDao.queryByModelId(modelId);
            if (model == null) {
                continue;
            }
            result.add(AiResourceOwnerVO.builder()
                    .resourceId(model.getModelId())
                    .resourceName(model.getModelName())
                    .ownerId(model.getOwnerId())
                    .build());
        }
        return result;
    }

    @Override
    public List<String> queryModelIdsByClientIds(List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return List.of();
        }

        List<String> modelIds = new ArrayList<>();
        for (String clientId : clientIds) {
            // 与归属校验的历史语义保持一致：不过滤 status（这里问的是"引用了谁的资源"）
            List<AiClientConfig> relations = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);
            for (AiClientConfig relation : relations) {
                if (AI_CLIENT_MODEL.getCode().equals(relation.getTargetType()) && hasText(relation.getTargetId())) {
                    modelIds.add(relation.getTargetId());
                }
            }
        }
        return modelIds;
    }

    @Override
    public List<AiAgentClientFlowConfigVO> queryUserOwnFlowConfigs(String aiAgentId, String ownerId) {
        List<AiAgentClientFlowConfigVO> result = new ArrayList<>();
        if (!hasText(aiAgentId) || !hasText(ownerId)) {
            return result;
        }

        List<AiAgentFlowConfig> flowConfigs = aiAgentFlowConfigDao.queryEnabledByAgentIdAndOwner(aiAgentId, ownerId);
        for (AiAgentFlowConfig flowConfig : flowConfigs) {
            result.add(AiAgentClientFlowConfigVO.builder()
                    .clientId(flowConfig.getClientId())
                    .clientName(flowConfig.getClientName())
                    .clientType(flowConfig.getClientType())
                    .sequence(flowConfig.getSequence())
                    .stepPrompt(flowConfig.getStepPrompt())
                    .build());
        }
        return result;
    }

    @Override
    public AiResourceOwnerVO queryApiChannelOwner(String apiId) {
        if (!hasText(apiId)) {
            return null;
        }
        AiClientApi api = aiClientApiDao.queryByApiId(apiId);
        if (api == null) {
            return null;
        }
        return AiResourceOwnerVO.builder()
                .resourceId(api.getApiId())
                .ownerId(api.getOwnerId())
                .build();
    }

    @Override
    public List<AiAgentTaskScheduleVO> queryAllValidTaskSchedule() {
        List<AiAgentTaskSchedule> aiAgentTaskSchedules = aiAgentTaskScheduleDao.queryAllValidTaskSchedule();

        List<AiAgentTaskScheduleVO> result = new ArrayList<>();
        for (AiAgentTaskSchedule taskSchedule : aiAgentTaskSchedules) {
            AiAgentTaskScheduleVO taskScheduleVO = AiAgentTaskScheduleVO.builder()
                    .id(taskSchedule.getId())
                    .agentId(taskSchedule.getAgentId())
                    .description(taskSchedule.getDescription())
                    .cronExpression(taskSchedule.getCronExpression())
                    .taskParam(taskSchedule.getTaskParam())
                    .build();
            result.add(taskScheduleVO);
        }

        return result;
    }

    @Override
    public List<Long> queryAllInvalidTaskScheduleIds() {
        return aiAgentTaskScheduleDao.queryAllInvalidTaskScheduleIds();
    }

    @Override
    public void createTagOrder(AiRagOrderVO aiRagOrderVO) {
        AiClientRagOrder aiRagOrder = new AiClientRagOrder();
        aiRagOrder.setRagId(aiRagOrderVO.getRagId() != null && !aiRagOrderVO.getRagId().isEmpty() ? aiRagOrderVO.getRagId() : java.util.UUID.randomUUID().toString());
        aiRagOrder.setRagName(aiRagOrderVO.getRagName());
        aiRagOrder.setKnowledgeTag(aiRagOrderVO.getKnowledgeTag());
        aiRagOrder.setStatus(1);
        aiRagOrder.setVersion(1);
        aiClientRagOrderDao.insert(aiRagOrder);
    }

    /**
     * 根据智能体状态查询查询所有可用的智能体
     * @return 可用的智能体列表
     */
    @Override
    public List<AiAgentVO> queryAvailableAgents() {
        List<AiAgent> aiAgents = aiAgentDao.queryEnabledAgents();
        List<AiAgentVO> aiAgentVOS = new ArrayList<>();
        for (AiAgent aiAgent : aiAgents) {
            aiAgentVOS.add(AiAgentVO.builder()
                    .agentId(aiAgent.getAgentId())
                    .agentName(aiAgent.getAgentName())
                    .description(aiAgent.getDescription())
                    .channel(aiAgent.getChannel())
                    .strategy(aiAgent.getStrategy())
                    .status(aiAgent.getStatus())
                    .ownerId(aiAgent.getOwnerId())
                    .build());
        }
        return aiAgentVOS;
    }

    @Override
    public List<AiClientApiVO> queryAiClientApiVOListByApiIds(List<String> apiIdList) {
        List<AiClientApiVO> aiClientApiVOS = new ArrayList<>();
        if (apiIdList == null || apiIdList.isEmpty()) {
            return aiClientApiVOS;
        }

        for (String apiId : apiIdList) {
            AiClientApi aiClientApi = aiClientApiDao.queryByApiId(apiId);
            // apiId 在 ai_client_api 中不存在（如 ai_client.api_id 悬空）时 queryByApiId 返回 null，
            // 直接取属性会 NPE 并中断整个 Armory 装配，这里跳过该条并告警
            if (aiClientApi == null) {
                log.warn("AI客户端API配置不存在，跳过，apiId：{}", apiId);
                continue;
            }
            aiClientApiVOS.add(AiClientApiVO.builder()
                    .apiId(aiClientApi.getApiId())
                    .baseUrl(aiClientApi.getBaseUrl())
                    .apiKey(aiClientApi.getApiKey())
                    .completionsPath(aiClientApi.getCompletionsPath())
                    .embeddingsPath(aiClientApi.getEmbeddingsPath())
                    .build());
        }
        return aiClientApiVOS;
    }

    @Override
    public boolean updateRagOrder(String ragId, String fileHash, String updateReason, Integer version) {
        LambdaQueryWrapper<AiClientRagOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiClientRagOrder::getRagId, ragId);
        
        AiClientRagOrder order = aiClientRagOrderDao.selectOne(wrapper);
        if (order == null) {
            log.error("知识库配置不存在: {}", ragId);
            return false;
        }

        order.setVersion(version);
        order.setFileHash(fileHash);
        order.setUpdateReason(updateReason);
        order.setUpdateTime(LocalDateTime.now());
        
        int rows = aiClientRagOrderDao.updateById(order);
        return rows > 0;
    }

    /**
     * 状态值为 1 视为启用；null 视为未启用，避免 Integer 拆箱 NPE。
     */
    private boolean isEnabled(Integer status) {
        return Integer.valueOf(1).equals(status);
    }

    /**
     * 非空白判定（不引 Spring StringUtils，保持仓储的薄依赖）。
     */
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

    @Override
    public AiRagOrderVO queryRagOrderById(String ragId) {
        LambdaQueryWrapper<AiClientRagOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiClientRagOrder::getRagId, ragId);
        
        AiClientRagOrder order = aiClientRagOrderDao.selectOne(wrapper);
        if (order == null) {
            return null;
        }

        return AiRagOrderVO.builder()
                .ragId(order.getRagId())
                .ragName(order.getRagName())
                .knowledgeTag(order.getKnowledgeTag())
                .status(order.getStatus())
                .version(order.getVersion())
                .fileHash(order.getFileHash())
                .updateReason(order.getUpdateReason())
                .createTime(order.getCreateTime())
                .updateTime(order.getUpdateTime())
                .build();
    }

}
