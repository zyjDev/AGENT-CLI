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
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();

                    // 2. 通过modelId查询模型配置，获取apiId
                    AiClientModel model = aiClientModelDao.queryByModelId(modelId);
                    if (model != null && model.getStatus() == 1) {
                        String apiId = model.getApiId();

                        // 3. 通过apiId查询API配置信息
                        AiClientApi apiConfig = aiClientApiDao.queryByApiId(apiId);
                        if (apiConfig != null && apiConfig.getStatus() == 1) {
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
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();

                    // 2. 通过modelId查询模型配置
                    AiClientModel model = aiClientModelDao.queryByModelId(modelId);
                    if (model != null && model.getStatus() == 1) {

                        // 3. 查询该模型关联的tool_mcp配置
                        List<AiClientConfig> toolMcpConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                        List<String> toolMcpIds = new ArrayList<>();
                        for (AiClientConfig toolMcpConfig : toolMcpConfigs) {
                            if (AI_CLIENT_TOOL_MCP.getCode().equals(toolMcpConfig.getTargetType()) && toolMcpConfig.getStatus() == 1) {
                                toolMcpIds.add(toolMcpConfig.getTargetId());
                            }
                        }

                        // 4. 构建模型VO
                        AiClientModelVO modelVO = AiClientModelVO.builder()
                                .modelId(model.getModelId())
                                .modelName(model.getModelName())
                                .apiId(model.getApiId())
                                .modelType(model.getModelType())
                                .typeName(model.getTypeName())
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
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();

                    // 2. 查询该模型关联的tool_mcp配置
                    List<AiClientConfig> toolMcpConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                    for (AiClientConfig toolMcpConfig : toolMcpConfigs) {
                        if (AI_CLIENT_TOOL_MCP.getCode().equals(toolMcpConfig.getTargetType()) && toolMcpConfig.getStatus() == 1) {
                            String toolMcpId = toolMcpConfig.getTargetId();

                            // 3. 查询tool_mcp配置信息
                            AiClientToolMcp toolMcp = aiClientToolMcpDao.queryByMcpId(toolMcpId);
                            if (toolMcp != null && toolMcp.getStatus() == 1) {
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
            // 1. 通过clientId查询关联的modelId
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();

                    // 2. 查询该模型关联的system_prompt配置
                    List<AiClientConfig> promptConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                    for (AiClientConfig promptConfig : promptConfigs) {
                        if (AI_CLIENT_SYSTEM_PROMPT.getCode().equals(promptConfig.getTargetType()) && promptConfig.getStatus() == 1) {
                            String promptId = promptConfig.getTargetId();

                            // 3. 查询system_prompt配置信息
                            AiClientSystemPrompt prompt = aiClientSystemPromptDao.queryByPromptId(promptId);
                            if (prompt != null && prompt.getStatus() == 1) {
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
            // 1. 通过clientId查询关联的modelId
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();

                    // 2. 查询该模型关联的system_prompt配置
                    List<AiClientConfig> promptConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                    for (AiClientConfig promptConfig : promptConfigs) {
                        if (AI_CLIENT_SYSTEM_PROMPT.getCode().equals(promptConfig.getTargetType()) && promptConfig.getStatus() == 1) {
                            String promptId = promptConfig.getTargetId();

                            // 3. 查询system_prompt配置信息
                            AiClientSystemPrompt prompt = aiClientSystemPromptDao.queryByPromptId(promptId);
                            if (prompt != null && prompt.getStatus() == 1) {
                                AiClientSystemPromptVO promptVO = AiClientSystemPromptVO.builder()
                                        .promptId(prompt.getPromptId())
                                        .promptName(prompt.getPromptName())
                                        .promptContent(prompt.getPromptContent())
                                        .description(prompt.getDescription())
                                        .status(prompt.getStatus())
                                        .build();

                                result.put(clientId, promptVO);
                            }
                        }
                    }
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
                if (AI_CLIENT_ADVISOR.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String advisorId = config.getTargetId();

                    // 2. 查询advisor配置信息
                    AiClientAdvisor advisor = aiClientAdvisorDao.queryByAdvisorId(advisorId);
                    if (advisor != null && advisor.getStatus() == 1) {
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
            if (aiClient != null && aiClient.getStatus() == 1) {
                AiClientVO clientVO = AiClientVO.builder()
                        .clientId(aiClient.getClientId())
                        .clientName(aiClient.getClientName())
                        .clientDesc(aiClient.getClientDesc())
                        .status(aiClient.getStatus())
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
            if (model != null && model.getStatus() == 1) {
                String apiId = model.getApiId();

                // 2. 通过apiId查询API配置信息
                AiClientApi apiConfig = aiClientApiDao.queryByApiId(apiId);
                if (apiConfig != null && apiConfig.getStatus() == 1) {
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
            if (model != null && model.getStatus() == 1) {
                AiClientModelVO modelVO = AiClientModelVO.builder()
                        .modelId(model.getModelId())
                        .modelName(model.getModelName())
                        .apiId(model.getApiId())
                        .modelType(model.getModelType())
                        .typeName(model.getTypeName())
                        .status(model.getStatus())
                        .build();

                result.add(modelVO);
            }
        }

        return result;
    }

    @Override
    public Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId) {
        try {
            List<AiAgentFlowConfig> flowConfigs = aiAgentFlowConfigDao.queryByAgentId(aiAgentId);
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

        return AiAgentVO.builder()
                .agentId(aiAgent.getAgentId())
                .agentName(aiAgent.getAgentName())
                .description(aiAgent.getDescription())
                .channel(aiAgent.getChannel())
                .strategy(aiAgent.getStrategy())
                .status(aiAgent.getStatus())
                .build();
    }

    /**
     * 根据智能体ID查询智能体关联的客户端模型
     * @param aiAgentId 智能体ID
     * @return 客户端模型列表
     */
    @Override
    public List<AiAgentClientFlowConfigVO> queryAiAgentClientsByAgentId(String aiAgentId) {
        List<AiAgentClientFlowConfigVO> aiAgentClientFlowConfigVOS = new ArrayList<>();

        List<AiAgentFlowConfig> flowConfigs = aiAgentFlowConfigDao.queryByAgentId(aiAgentId);
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
                    .build());
        }
        return aiAgentVOS;
    }

    @Override
    public List<AiClientApiVO> queryAiClientApiVOListByApiIds(List<String> apiIdList) {
        List<AiClientApiVO> aiClientApiVOS = new ArrayList<>();
        for (String apiId : apiIdList) {
            AiClientApi aiClientApi = aiClientApiDao.queryByApiId(apiId);
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
