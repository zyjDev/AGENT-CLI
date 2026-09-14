package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiAgentDrawAdminService;
import cn.bugstack.ai.api.dto.AiAgentDrawConfigRequestDTO;
import cn.bugstack.ai.api.dto.AiAgentDrawConfigResponseDTO;
import cn.bugstack.ai.api.dto.AiAgentDrawConfigQueryRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.*;
import cn.bugstack.ai.infrastructure.dao.po.AiAgent;
import cn.bugstack.ai.infrastructure.dao.po.AiAgentDrawConfig;
import cn.bugstack.ai.infrastructure.dao.po.AiAgentFlowConfig;
import cn.bugstack.ai.infrastructure.dao.po.AiClientConfig;
import cn.bugstack.ai.trigger.http.admin.util.DrawConfigParser;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 拖拉拽， 方便快速装配agent，不需要去数据库中进行配置
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/9/28 07:35
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-agent-draw")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class AiAgentDrawAdminController implements IAiAgentDrawAdminService {

    @Resource
    private IAiAgentDrawConfigDao aiAgentDrawConfigDao;
    @Resource
    private IAiClientConfigDao aiClientConfigDao;
    @Resource
    private IAiAgentDao aiAgentDao;
    @Resource
    private IAiAgentFlowConfigDao aiAgentFlowConfigDao;

    @Override
    @PostMapping("/query-list")
    public Response<List<AiAgentDrawConfigResponseDTO>> queryDrawConfigList(@RequestBody AiAgentDrawConfigQueryRequestDTO request) {
        try {
            log.info("查询拖拉拽流程图配置列表请求：{}", request);

            List<AiAgentDrawConfig> configs;

            // 条件查询
            if (StringUtils.hasText(request.getConfigId())) {
                AiAgentDrawConfig cfg = aiAgentDrawConfigDao.queryByConfigId(request.getConfigId());
                configs = cfg != null ? List.of(cfg) : List.of();
            } else if (StringUtils.hasText(request.getConfigName())) {
                configs = aiAgentDrawConfigDao.queryByConfigName(request.getConfigName());
            } else if (StringUtils.hasText(request.getAgentId())) {
                AiAgentDrawConfig cfg = aiAgentDrawConfigDao.queryByAgentId(request.getAgentId());
                configs = cfg != null ? List.of(cfg) : List.of();
            } else if (request.getStatus() != null) {
                if (request.getStatus().equals(1)) {
                    configs = aiAgentDrawConfigDao.queryEnabledConfigs();
                } else {
                    configs = aiAgentDrawConfigDao.queryAll();
                }
            } else {
                configs = aiAgentDrawConfigDao.queryAll();
            }

            // 简单分页（内存分页）
            if (request.getPageNum() != null && request.getPageSize() != null) {
                int pageNum = Math.max(1, request.getPageNum());
                int pageSize = Math.max(1, request.getPageSize());
                int start = (pageNum - 1) * pageSize;
                int end = Math.min(start + pageSize, configs.size());
                if (start < configs.size()) {
                    configs = configs.subList(start, end);
                } else {
                    configs = List.of();
                }
            }

            // PO 转 DTO
            List<AiAgentDrawConfigResponseDTO> responseDTOs = new ArrayList<>();
            for (AiAgentDrawConfig config : configs) {
                AiAgentDrawConfigResponseDTO dto = new AiAgentDrawConfigResponseDTO();
                BeanUtils.copyProperties(config, dto);
                responseDTOs.add(dto);
            }

            return Response.<List<AiAgentDrawConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询拖拉拽流程图配置列表失败", e);
            return Response.<List<AiAgentDrawConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    /**
     * 保存拖拉拽流程图配置（新建 / 更新共用）
     * <p>
     * <b>事务语义</b>：本方法带 {@code @Transactional(rollbackFor = Exception.class)}，
     * 因此**任何失败都必须以异常外抛的方式结束** —— 在方法内部 {@code catch} 后 {@code return}
     * 会让 Spring 感知不到异常，事务照常提交，从而留下半成品配置（ai_agent / ai_agent_draw_config
     * 已写入，而关系表写入失败）。异常统一交给 {@code GlobalExceptionHandler} 转成错误响应。
     */
    @Override
    @PostMapping("/save-config")
    @Transactional(rollbackFor = Exception.class)
    public Response<String> saveDrawConfig(@RequestBody AiAgentDrawConfigRequestDTO request) {
        log.info("保存流程图配置请求：{}", request);

        // 1. 参数校验（前置：避免为非法请求做无意义的 agentId 生成与库操作）
        if (!StringUtils.hasText(request.getConfigName())) {
            return Response.<String>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("配置名称不能为空")
                    .build();
        }

        if (!StringUtils.hasText(request.getConfigData())) {
            return Response.<String>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("配置数据不能为空")
                    .build();
        }

        // 2. 解析配置ID，并据此判定本次是「新建」还是「更新」
        String configId = StringUtils.hasText(request.getConfigId())
                ? request.getConfigId()
                : UUID.randomUUID().toString().replace("-", "");
        AiAgentDrawConfig existingConfig = aiAgentDrawConfigDao.queryByConfigId(configId);

        // 3. 决定 agentId
        //    更新：必须复用原 agentId。原实现在更新分支同样重新生成 agentId 并 insert 一条新 agent，
        //          导致每次编辑都遗留一条孤儿 ai_agent 记录 + 一批孤儿 ai_agent_flow_config，
        //          并切断 ai_agent_task_schedule 对原 agentId 的引用（定时任务因此指向失效智能体）。
        //    新建：生成一个未被占用的 agentId。
        String agentId = (existingConfig != null && StringUtils.hasText(existingConfig.getAgentId()))
                ? existingConfig.getAgentId()
                : generateUniqueAgentId();
        request.setAgentId(agentId);

        // 4. 解析JSON中的agent信息
        String[] agentInfo = parseAgentInfoFromJson(request.getConfigData());
        String agentName = agentInfo[0];
        String description = agentInfo[1];
        String channel = agentInfo[2];
        String strategy = agentInfo[3];

        // 5. 写入 ai_agent：不存在则 insert，存在则更新元信息
        //    注意 ai_agent.agent_id 上有唯一索引 uk_agent_id，更新场景绝不能重复 insert
        AiAgent existingAgent = aiAgentDao.queryByAgentId(agentId);
        if (existingAgent == null) {
            aiAgentDao.insert(AiAgent.builder()
                    .agentId(agentId)
                    .agentName(agentName)
                    .channel(channel)
                    .strategy(strategy)
                    .status(1)
                    .description(description)
                    .build());
        } else {
            // 只更新元信息，不动 status —— 编辑流程图不应隐式把已禁用的智能体重置为启用
            AiAgent updateAgent = new AiAgent();
            updateAgent.setId(existingAgent.getId());
            updateAgent.setAgentName(agentName);
            updateAgent.setChannel(channel);
            updateAgent.setStrategy(strategy);
            updateAgent.setDescription(description);
            updateAgent.setUpdateTime(LocalDateTime.now());
            aiAgentDao.updateById(updateAgent);
        }

        // 6. 写入 ai_agent_draw_config
        AiAgentDrawConfig drawConfig = new AiAgentDrawConfig();
        BeanUtils.copyProperties(request, drawConfig);
        drawConfig.setConfigId(configId);
        drawConfig.setAgentId(agentId);
        drawConfig.setStatus(1); // 默认启用状态

        int result;
        if (existingConfig != null) {
            // 更新现有配置
            drawConfig.setId(existingConfig.getId());
            drawConfig.setVersion(existingConfig.getVersion() + 1);
            drawConfig.setUpdateTime(LocalDateTime.now());
            result = aiAgentDrawConfigDao.updateByConfigId(drawConfig);
            log.info("更新流程图配置，configId: {}, result: {}", configId, result);
        } else {
            // 创建新配置
            drawConfig.setVersion(1); // 默认版本号
            drawConfig.setCreateTime(LocalDateTime.now());
            drawConfig.setUpdateTime(LocalDateTime.now());
            result = aiAgentDrawConfigDao.insert(drawConfig);
            log.info("创建流程图配置，configId: {}, result: {}", configId, result);
        }

        if (result <= 0) {
            // 抛异常而不是返回错误码：只有异常外抛才会触发事务回滚，
            // 否则上面已写入的 ai_agent 会残留成孤儿数据。
            // 说明：JDBC URL 未开启 useAffectedRows，MySQL 返回的是「匹配行数」，
            // 因此 result == 0 代表确实没匹配到配置行（而非「值未变化」），可安全视为失败。
            throw new BizException(ResponseCode.UN_ERROR.getCode(), "保存流程图配置失败，configId=" + configId);
        }

        // 7. 重建 ai_client_config 关系
        //    不再用 try/catch 吞异常 —— 关系写失败说明配置不完整，必须让整个事务回滚，
        //    否则会产生「接口返回成功、但流程缺客户端」的半成品配置。
        List<AiClientConfig> configRelations = DrawConfigParser.parseConfigData(request.getConfigData());
        if (!configRelations.isEmpty()) {
            // 【注意】此处原本有一段「更新时先删旧关系」的逻辑：aiClientConfigDao.deleteBySourceId(configId)。
            // 它其实是一条**无效清理**：ai_client_config 没有 config_id 列，其 source_id / target_id 存的是
            // 节点引用的客户端 / 模型 ID（见 DrawConfigParser.createAiClientConfig；库中实际值为 '2000'、'3001' 等），
            // 拿 32 位 configId 去匹配 source_id 永远查不到任何行。故已移除，避免留下「看起来在清理」的假象。
            // 目前的去重依赖下面「已存在则跳过」的判断（全局去重，不按配置隔离）。
            // 已知遗留设计缺陷（需给 ai_client_config 增加 config_id 列才能根治）：
            //   1) 删除某条 draw config 时，它写入的关系行不会被清理；
            //   2) 多条 draw config 若引用同一组节点，会共用同一批关系行，且 ext_param 里的 configId 归属会失真。

            // 批量插入新的关系数据
            for (AiClientConfig config : configRelations) {
                // 检查是否已经存在相同的记录
                List<AiClientConfig> existingConfigs = aiClientConfigDao.queryByConditions(
                        config.getSourceType(),
                        config.getSourceId(),
                        config.getTargetType(),
                        config.getTargetId()
                );

                if (existingConfigs.isEmpty()) {
                    // 设置扩展参数，记录来源配置ID
                    config.setExtParam("{\"configId\":\"" + configId + "\"}");
                    aiClientConfigDao.insert(config);
                    log.debug("插入新的配置关系: sourceType={}, sourceId={}, targetType={}, targetId={}",
                            config.getSourceType(), config.getSourceId(), config.getTargetType(), config.getTargetId());
                } else {
                    log.debug("配置关系已存在，跳过插入: sourceType={}, sourceId={}, targetType={}, targetId={}",
                            config.getSourceType(), config.getSourceId(), config.getTargetType(), config.getTargetId());
                }
            }
            log.info("成功保存{}条配置关系数据", configRelations.size());
        }

        // 8. 重建 ai_agent_flow_config 关系
        //    先按 agentId 清空旧数据再重建。原实现在更新分支执行 deleteByAgentId(新生成的 agentId)，
        //    而该 agentId 是刚生成的、必然查不到任何旧数据 —— 等于没清理；
        //    同时「新配置里没有 client 节点」时会整个跳过清理，导致旧流程配置残留。
        List<AiAgentFlowConfig> agentFlowConfigs = parseClientInfoFromJson(request.getConfigData(), agentId);
        aiAgentFlowConfigDao.deleteByAgentId(agentId);
        for (AiAgentFlowConfig flowConfig : agentFlowConfigs) {
            aiAgentFlowConfigDao.insert(flowConfig);
        }
        log.info("成功保存{}条agent-client关系数据", agentFlowConfigs.size());

        return Response.<String>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(configId)
                .build();
    }

    /**
     * 生成一个未被占用的 8 位数字 agentId
     * <p>
     * 原实现为 {@code String.format("%08d", System.currentTimeMillis() % 100000000L)}：
     * 取模后约 <b>27.8 小时回绕一次</b>，且同一毫秒内的并发请求会得到相同值，
     * 而 {@code ai_agent.agent_id} 上有唯一索引 {@code uk_agent_id}，重复会直接抛 DuplicateKeyException。
     * <p>
     * 这里保留 8 位数字格式（{@code ai_agent_task_schedule.agent_id} 是 bigint，不能改用 UUID），
     * 改为「随机取值 + 存在性校验 + 有限重试」。
     */
    private String generateUniqueAgentId() {
        for (int i = 0; i < 5; i++) {
            String candidate = String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
            if (aiAgentDao.queryByAgentId(candidate) == null) {
                return candidate;
            }
            log.warn("agentId {} 已被占用，重新生成（第 {} 次）", candidate, i + 1);
        }
        throw new BizException(ResponseCode.UN_ERROR.getCode(), "生成唯一 agentId 失败，请重试");
    }

    /**
     * 解析JSON配置数据中的agent信息
     *
     * @param configData JSON配置数据
     * @return agent信息数组 [agentName, channel]
     */
    private String[] parseAgentInfoFromJson(String configData) {
        String[] agentInfo = new String[]{"", "", "", ""}; // 默认值

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(configData);
            JsonNode nodesArray = rootNode.get("nodes");

            if (nodesArray != null && nodesArray.isArray()) {
                for (JsonNode node : nodesArray) {
                    String nodeType = node.get("type").asText();

                    // 只处理type为"agent"的节点
                    if ("agent".equals(nodeType)) {
                        JsonNode dataNode = node.get("data");
                        if (dataNode != null) {
                            JsonNode inputsValuesNode = dataNode.get("inputsValues");
                            if (inputsValuesNode != null) {
                                log.debug("开始解析agent节点的inputsValues: {}", inputsValuesNode.toString());
                                
                                // 提取agent信息
                                String agentName = extractValueFromInputs(inputsValuesNode, "agentName");
                                String description = extractValueFromInputs(inputsValuesNode, "description");
                                String channel = extractValueFromInputs(inputsValuesNode, "channel");
                                String strategy = extractValueFromInputs(inputsValuesNode, "strategy");

                                agentInfo[0] = agentName != null ? agentName : "";
                                agentInfo[1] = description != null ? description : "";
                                agentInfo[2] = channel != null ? channel : "";
                                agentInfo[3] = strategy != null ? strategy : "";

                                log.info("解析到agent信息: agentName={}, description={}, channel={}, strategy={}", 
                                        agentName, description, channel, strategy);
                                break; // 找到第一个agent节点就退出
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析JSON配置数据中的agent信息失败", e);
            // 返回默认值，不抛出异常，避免影响整个保存流程
        }

        return agentInfo;
    }

    /**
     * 解析JSON配置数据中的client信息
     *
     * @param configData JSON配置数据
     * @param agentId    智能体ID
     * @return agent-client关系配置列表
     */
    private List<AiAgentFlowConfig> parseClientInfoFromJson(String configData, String agentId) {
        List<AiAgentFlowConfig> agentFlowConfigs = new ArrayList<>();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(configData);
            JsonNode nodesArray = rootNode.get("nodes");

            if (nodesArray != null && nodesArray.isArray()) {
                for (JsonNode node : nodesArray) {
                    String nodeType = node.get("type").asText();

                    // 只处理type为"client"的节点
                    if ("client".equals(nodeType)) {
                        JsonNode dataNode = node.get("data");
                        if (dataNode != null) {
                            JsonNode inputsValuesNode = dataNode.get("inputsValues");
                            if (inputsValuesNode != null) {
                                // 提取client信息
                                String clientType = extractValueFromInputs(inputsValuesNode, "clientType");
                                String clientId = extractValueFromInputs(inputsValuesNode, "clientId");
                                String clientName = extractValueFromInputs(inputsValuesNode, "clientName");
                                Integer sequence = extractIntegerValueFromInputs(inputsValuesNode, "sequence");
                                String stepPrompt = extractValueFromInputs(inputsValuesNode, "stepPrompt");

                                // 创建AiAgentFlowConfig对象
                                AiAgentFlowConfig flowConfig = AiAgentFlowConfig.builder()
                                        .agentId(agentId)
                                        .clientId(clientId)
                                        .clientName(clientName)
                                        .clientType(clientType)
                                        .sequence(sequence)
                                        .stepPrompt(stepPrompt)
                                        // 新保存的流程节点显式置为有效；否则 status 为 null 时只能依赖 DB 默认值
                                        .status(1)
                                        .createTime(LocalDateTime.now())
                                        .build();

                                agentFlowConfigs.add(flowConfig);
                                log.info("解析到client信息: clientType={}, clientName={}, sequence={}",
                                        clientType, clientName, sequence);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析JSON配置数据失败", e);
            throw new RuntimeException("解析JSON配置数据失败", e);
        }

        return agentFlowConfigs;
    }

    /**
     * 从inputsValues中提取字符串值
     *
     * @param inputsValuesNode inputsValues节点
     * @param fieldName        字段名
     * @return 字段值
     */
    private String extractValueFromInputs(JsonNode inputsValuesNode, String fieldName) {
        JsonNode fieldNode = inputsValuesNode.get(fieldName);
        log.debug("提取字段 '{}': fieldNode={}", fieldName, fieldNode != null ? fieldNode.toString() : "null");
        
        if (fieldNode != null) {
            // 处理数组格式：[{"key": "xxx", "value": "yyy"}] 或 [{"key": "xxx", "value": {"content": "yyy"}}]
            if (fieldNode.isArray() && !fieldNode.isEmpty()) {
                JsonNode firstItem = fieldNode.get(0);
                if (firstItem != null) {
                    JsonNode valueNode = firstItem.get("value");
                    log.debug("字段 '{}' 数组格式，valueNode={}", fieldName, valueNode != null ? valueNode.toString() : "null");
                    
                    if (valueNode != null) {
                        // 如果value是对象，尝试获取content字段
                        if (valueNode.isObject()) {
                            JsonNode contentNode = valueNode.get("content");
                            if (contentNode != null) {
                                String result = contentNode.asText();
                                log.debug("字段 '{}' 从content获取值: {}", fieldName, result);
                                return result;
                            }
                        }
                        // 如果value是字符串，直接返回
                        else if (valueNode.isTextual()) {
                            String result = valueNode.asText();
                            log.debug("字段 '{}' 直接获取字符串值: {}", fieldName, result);
                            return result;
                        }
                        // 如果value是数字，转换为字符串
                        else if (valueNode.isNumber()) {
                            String result = valueNode.asText();
                            log.debug("字段 '{}' 数字转字符串值: {}", fieldName, result);
                            return result;
                        }
                    }
                }
            }
            // 处理直接字符串格式："fieldName": "value"
            else if (fieldNode.isTextual()) {
                String result = fieldNode.asText();
                log.debug("字段 '{}' 直接字符串格式值: {}", fieldName, result);
                return result;
            }
        }
        
        log.debug("字段 '{}' 未找到有效值", fieldName);
        return null;
    }

    /**
     * 从inputsValues中提取整数值
     *
     * @param inputsValuesNode inputsValues节点
     * @param fieldName        字段名
     * @return 字段值
     */
    private Integer extractIntegerValueFromInputs(JsonNode inputsValuesNode, String fieldName) {
        JsonNode fieldNode = inputsValuesNode.get(fieldName);
        if (fieldNode != null) {
            // 处理数组格式：[{"key": "xxx", "value": 123}] 或 [{"key": "xxx", "value": {"content": 123}}]
            if (fieldNode.isArray() && fieldNode.size() > 0) {
                JsonNode firstItem = fieldNode.get(0);
                if (firstItem != null) {
                    JsonNode valueNode = firstItem.get("value");
                    if (valueNode != null) {
                        // 如果value是对象，尝试获取content字段
                        if (valueNode.isObject()) {
                            JsonNode contentNode = valueNode.get("content");
                            if (contentNode != null && contentNode.isNumber()) {
                                return contentNode.asInt();
                            }
                        }
                        // 如果value是数字，直接返回
                        else if (valueNode.isNumber()) {
                            return valueNode.asInt();
                        }
                        // 如果value是字符串，尝试转换为数字
                        else if (valueNode.isTextual()) {
                            try {
                                return Integer.parseInt(valueNode.asText());
                            } catch (NumberFormatException e) {
                                log.warn("无法将字符串 '{}' 转换为整数", valueNode.asText());
                            }
                        }
                    }
                }
            }
            // 处理直接数值格式："fieldName": 123
            else if (fieldNode.isNumber()) {
                return fieldNode.asInt();
            }
        }
        return null;
    }

    @Override
    @GetMapping("/get-config/{configId}")
    public Response<AiAgentDrawConfigResponseDTO> getDrawConfig(@PathVariable("configId") String configId) {
        try {
            log.info("获取流程图配置请求，configId: {}", configId);

            if (!StringUtils.hasText(configId)) {
                return Response.<AiAgentDrawConfigResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("配置ID不能为空")
                        .build();
            }

            AiAgentDrawConfig drawConfig = aiAgentDrawConfigDao.queryByConfigId(configId);

            if (drawConfig == null) {
                return Response.<AiAgentDrawConfigResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("配置不存在")
                        .build();
            }

            AiAgentDrawConfigResponseDTO responseDTO = new AiAgentDrawConfigResponseDTO();
            BeanUtils.copyProperties(drawConfig, responseDTO);

            return Response.<AiAgentDrawConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();

        } catch (Exception e) {
            log.error("获取流程图配置失败", e);
            return Response.<AiAgentDrawConfigResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("获取失败：" + e.getMessage())
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-config/{configId}")
    @Transactional(rollbackFor = Exception.class)
    public Response<String> deleteDrawConfig(@PathVariable("configId") String configId) {
        try {
            log.info("删除流程图配置请求，configId: {}", configId);

            if (!StringUtils.hasText(configId)) {
                return Response.<String>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("配置ID不能为空")
                        .build();
            }

            // 1. 先查询配置详情获取agentId
            AiAgentDrawConfig drawConfig = aiAgentDrawConfigDao.queryByConfigId(configId);
            if (drawConfig == null) {
                return Response.<String>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("删除失败，配置不存在")
                        .build();
            }

            String agentId = drawConfig.getAgentId();
            log.info("删除流程图配置，configId: {}, agentId: {}", configId, agentId);

            // 2. 删除拖拉拽配置
            int drawConfigResult = aiAgentDrawConfigDao.deleteByConfigId(configId);
            log.info("删除拖拉拽配置结果: {}", drawConfigResult);

            // 3. 删除智能体配置
            if (StringUtils.hasText(agentId)) {
                int agentResult = aiAgentDao.deleteByAgentId(agentId);
                log.info("删除智能体配置结果: {}", agentResult);

                // 4. 删除智能体流程配置
                int flowConfigResult = aiAgentFlowConfigDao.deleteByAgentId(agentId);
                log.info("删除智能体流程配置结果: {}", flowConfigResult);
            }

            if (drawConfigResult > 0) {
                return Response.<String>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data("删除成功")
                        .build();
            } else {
                return Response.<String>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("删除失败，配置不存在")
                        .build();
            }

        } catch (Exception e) {
            log.error("删除流程图配置失败", e);
            throw new RuntimeException("删除流程图配置失败", e);
        }
    }
}
