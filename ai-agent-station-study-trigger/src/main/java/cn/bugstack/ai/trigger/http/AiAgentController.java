package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IAiAgentService;
import cn.bugstack.ai.api.dto.AiAgentResponseDTO;
import cn.bugstack.ai.api.dto.ArmoryAgentRequestDTO;
import cn.bugstack.ai.api.dto.ArmoryApiRequestDTO;
import cn.bugstack.ai.api.dto.AutoAgentRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentAccessVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.IAgentAccessService;
import cn.bugstack.ai.domain.agent.service.IAgentDispatchService;
import cn.bugstack.ai.domain.agent.service.IArmoryService;
import cn.bugstack.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.ai.types.context.UserContext;
import cn.bugstack.ai.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * AutoAgent 自动智能对话体
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AiAgentController implements IAiAgentService {

    /**
     * SSE 超时（毫秒）。
     * <p>
     * 原实现硬编码 30 分钟，过长：客户端断连或执行卡住时，连接与工作线程要挂满 30 分钟才释放。
     * 改为可配置，默认 10 分钟 —— 远大于 maxStep 次模型调用的正常耗时，同时不至于长时间占用资源。
     * 注意异常分支的 emitter 也必须带上同一超时值（无参构造 = 永不超时）。
     */
    @Value("${xfg.ai.sse-timeout-millis:600000}")
    private long sseTimeoutMillis;

    // 注入策略调度器
    @Resource
    private IAgentDispatchService agentDispatchService;
    // 注入装配服务
    @Resource
    private IArmoryService armoryService;
    // 归属与可执行性校验（领域服务）。
    // 改造前这里是 3 个 infrastructure DAO + OwnModelGuard：规则散落在 HTTP 层，
    // 且同一次请求里把同一份智能体数据查了 3 次；现已全部收敛进领域层，本类只做参数装配与响应转换。
    @Resource
    private IAgentAccessService agentAccessService;
    @Resource
    private ApplicationContext applicationContext;

    /** 以 SSE 事件的形式回错误（必须是合法 JSON，前端按 data: {type,content} 解析） */
    private ResponseBodyEmitter sseError(String message) {
        return sseError(message, null);
    }

    /**
     * 以 SSE 事件的形式回错误，并带上业务错误码。
     * <p>
     * 带码是为了让前端能针对特定错误给出可操作的动作（例如缺少自己的模型 Key 时弹「去配置」按钮），
     * 而不是只丢一句文案让用户自己猜去哪儿配。
     */
    private ResponseBodyEmitter sseError(String message, String code) {
        ResponseBodyEmitter errorEmitter = new ResponseBodyEmitter(sseTimeoutMillis);
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("type", "error");
            payload.put("subType", null);
            payload.put("code", code);
            payload.put("content", message);
            errorEmitter.send("data: " + JSON.toJSONString(payload) + "\n\n");
            errorEmitter.complete();
        } catch (Exception ex) {
            log.error("发送错误信息失败：{}", ex.getMessage(), ex);
        }
        return errorEmitter;
    }

    /**
     * 解析归属与可执行性（<b>一次查库</b>，替代改造前「三次查询 + 三条独立校验」），
     * 并对"被拒"的情况统一打日志。
     *
     * <p>规则本体在领域服务 {@link IAgentAccessService}：管理员不受限；普通用户自建的智能体
     * 链路必须都是他自己的；平台默认智能体必须先绑定自己的 Key。
     * 这里只负责把结论翻成协议层的响应。
     */
    private AiAgentAccessVO resolveAccess(String agentId) {
        AiAgentAccessVO access = agentAccessService.resolveAccess(agentId, UserContext.userId(), UserContext.isAdmin());
        if (!access.isAccessible()) {
            log.warn("拒绝调用无权限的智能体，aiAgentId={}, userId={}", agentId, UserContext.userId());
        } else if (access.getOwnModelProblem() != null) {
            log.warn("拒绝调用：自建智能体借道了非本人资源，aiAgentId={}, userId={}", agentId, UserContext.userId());
        } else if (access.getBindingProblem() != null) {
            log.warn("拒绝调用：普通用户未绑定自己的 Key，aiAgentId={}, userId={}", agentId, UserContext.userId());
        }
        return access;
    }

    /**
     * 用户级链路「补装」。
     *
     * <p>后端重启后，用户绑定自己 Key 时注册的那套 Bean（{@code ai_client_<他的clientId>} 等）会丢，
     * 直接对话会报「找不到 Bean」。这里在对话前检查一次，缺了就重新装配 ——
     * 用户不需要知道"装配"是什么，他只是发现绑过之后一直能用。
     */
    private void ensureUserChainAssembled(String agentId) {
        String userId = UserContext.userId();
        if (userId == null || userId.isBlank()) {
            return;
        }
        // 只认"他自己的绑定"（不回落系统默认链路），否则会把平台默认链路当成需要补装的对象
        List<AiAgentClientFlowConfigVO> myBindings = agentAccessService.queryUserOwnFlowConfigs(agentId, userId);
        for (AiAgentClientFlowConfigVO binding : myBindings) {
            String beanName = AiAgentEnumVO.AI_CLIENT.getBeanName(binding.getClientId());
            if (applicationContext.containsBean(beanName)) {
                continue;
            }
            log.info("用户级链路 Bean 缺失，自动补装：agentId={}, userId={}, clientId={}",
                    agentId, userId, binding.getClientId());
            try {
                // 装配会把该智能体的全部流程配置（含他这条）都注册成 Bean，装一次即可
                armoryService.acceptArmoryAgent(agentId);
            } catch (Exception e) {
                log.warn("自动补装失败（本次对话可能因缺少 Bean 而失败）：agentId={}, userId={}", agentId, userId, e);
            }
            return;
        }
    }

    /**
     *  AutoAgent 流式执行
     * @param request
     * @param response
     * @return
     */
    @Override
    @RequestMapping(value = "auto_agent", method = RequestMethod.POST)
    public ResponseBodyEmitter autoAgent(@RequestBody AutoAgentRequestDTO request, HttpServletResponse response) {
        log.info("AutoAgent流式执行请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 设置SSE响应头
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            // 0. 归属与可执行性校验（一次解析拿全三个结论）：
            //    只能调用「公共 + 本人」的智能体；普通用户自建的智能体必须用自己的模型
            //    （公共/别人的都算借道）；平台默认智能体必须绑过用户自己的 Key（平台 Key 只给管理员用）。
            //    在服务端拦一道，避免绕过前端直接调接口白嫖平台 Key。
            AiAgentAccessVO access = resolveAccess(request.getAiAgentId());
            if (!access.isAccessible()) {
                return sseError("智能体不存在或无权访问");
            }
            if (access.getOwnModelProblem() != null) {
                return sseError(access.getOwnModelProblem(), ResponseCode.NEED_OWN_MODEL_KEY.getCode());
            }
            if (access.getBindingProblem() != null) {
                return sseError(access.getBindingProblem(), ResponseCode.NEED_OWN_MODEL_KEY.getCode());
            }

            // 0.1 用户级链路补装：后端重启后他的 Bean 会丢，首次对话时自动装回来
            ensureUserChainAssembled(request.getAiAgentId());

            // 1. 创建流式输出对象
            ResponseBodyEmitter emitter = new ResponseBodyEmitter(sseTimeoutMillis);

            // 2. 构建执行命令实体
            //    userId 一并带上：对话记忆按用户分区（跨线程执行时靠它区分，而不是靠线程上下文）
            ExecuteCommandEntity executeCommandEntity = ExecuteCommandEntity.builder()
                    .aiAgentId(request.getAiAgentId())
                    .message(request.getMessage())
                    .sessionId(request.getSessionId())
                    .maxStep(request.getMaxStep())
                    .knowledgeTag(request.getKnowledgeTag())
                    .userId(UserContext.userId())
                    .build();

            // 3. 调度处理
            agentDispatchService.dispatch(executeCommandEntity, emitter);

            return emitter;

        } catch (Exception e) {
            log.error("AutoAgent请求处理异常：{}", e.getMessage(), e);
            // 必须带上超时：无参构造的 ResponseBodyEmitter 永不超时，
            // 一旦客户端不再读取，这条错误连接会一直挂着
            ResponseBodyEmitter errorEmitter = new ResponseBodyEmitter(sseTimeoutMillis);
            try {
                errorEmitter.send("请求处理异常：" + e.getMessage());
                errorEmitter.complete();
            } catch (Exception ex) {
                log.error("发送错误信息失败：{}", ex.getMessage(), ex);
            }
            return errorEmitter;
        }
    }

    /**
     * 装配智能体
     * @param request ArmoryAgentRequestDTO
     * @return Response<Boolean>
     */
    @Override
    @RequestMapping(value = "armory_agent", method = RequestMethod.POST)
    public Response<Boolean> armoryAgent(@RequestBody ArmoryAgentRequestDTO request) {
        log.info("装配智能体请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 参数校验
            if (request == null || request.getAgentId() == null || request.getAgentId().trim().isEmpty()) {
                log.warn("装配智能体请求参数无效：agentId为空");
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("agentId不能为空")
                        .data(false)
                        .build();
            }

            // 归属与可执行性校验（一次解析拿全三个结论）：
            // 装配会把该智能体的资源注册成 Spring 单例 Bean，绝不能装配别人的私有智能体；
            // 自建智能体的链路必须都是他自己的；平台默认智能体必须先绑过他自己的 Key
            AiAgentAccessVO access = resolveAccess(request.getAgentId());
            if (!access.isAccessible()) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("智能体不存在或无权访问")
                        .data(false)
                        .build();
            }
            if (access.getOwnModelProblem() != null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.NEED_OWN_MODEL_KEY.getCode())
                        .info(access.getOwnModelProblem())
                        .data(false)
                        .build();
            }
            if (access.getBindingProblem() != null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.NEED_OWN_MODEL_KEY.getCode())
                        .info(access.getBindingProblem())
                        .data(false)
                        .build();
            }

            
            // 调用装配服务
            armoryService.acceptArmoryAgent(request.getAgentId());
            
            log.info("装配智能体成功，agentId：{}", request.getAgentId());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("装配成功")
                    .data(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("装配智能体失败，agentId：{}，错误信息：{}", 
                    request != null ? request.getAgentId() : "null", e.getMessage(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("装配失败：" + e.getMessage())
                    .data(false)
                    .build();
        }
    }

    /**
     * 查询可用智能体列表
     * @return Response<List<AiAgentResponseDTO>>
     */
    @Override
    @RequestMapping(value = "query_available_agents", method = RequestMethod.GET)
    public Response<List<AiAgentResponseDTO>> queryAvailableAgents() {
        log.info("查询可用智能体列表请求开始");

        try {
            // 调用装配服务查询可用智能体
            List<AiAgentVO> aiAgentVOList = armoryService.queryAvailableAgents();
            
            // 转换为响应DTO
            List<AiAgentResponseDTO> responseList = new ArrayList<>();
            for (AiAgentVO aiAgentVO : aiAgentVOList) {
                AiAgentResponseDTO responseDTO = AiAgentResponseDTO.builder()
                        .agentId(aiAgentVO.getAgentId())
                        .agentName(aiAgentVO.getAgentName())
                        .description(aiAgentVO.getDescription())
                        .channel(aiAgentVO.getChannel())
                        .strategy(aiAgentVO.getStrategy())
                        .status(aiAgentVO.getStatus())
                        .build();
                responseList.add(responseDTO);
            }
            
            log.info("查询可用智能体列表成功，共{}个智能体", responseList.size());
            return Response.<List<AiAgentResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("查询成功")
                    .data(responseList)
                    .build();
                    
        } catch (Exception e) {
            log.error("查询可用智能体列表失败，错误信息：{}", e.getMessage(), e);
            return Response.<List<AiAgentResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询失败：" + e.getMessage())
                    .data(new ArrayList<>())
                    .build();
        }
    }

    /**
     * 装配API
     * @param request ArmoryApiRequestDTO
     * @return Response<Boolean>
     */
    @Override
    @RequestMapping(value = "armory_api", method = RequestMethod.POST)
    public Response<Boolean> armoryApi(@RequestBody ArmoryApiRequestDTO request) {
        log.info("装配API请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 参数校验
            if (request == null || request.getApiId() == null || request.getApiId().trim().isEmpty()) {
                log.warn("装配API请求参数无效：apiId为空");
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("apiId不能为空")
                        .data(false)
                        .build();
            }
            
            // 归属校验：只能装配「自己」的 API 通道（系统默认的通道仅管理员可重新装配）。
            // 规则同样收在领域服务里，trigger 不再直连 DAO
            if (!agentAccessService.canWriteApiChannel(request.getApiId(), UserContext.userId(), UserContext.isAdmin())) {
                log.warn("拒绝装配无权限的 API 通道，apiId={}, userId={}", request.getApiId(), UserContext.userId());
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("API 通道不存在或无权操作")
                        .data(false)
                        .build();
            }

            // 调用装配服务
            armoryService.acceptArmoryAgentClientModelApi(request.getApiId());
            
            log.info("装配API成功，apiId：{}", request.getApiId());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("装配成功")
                    .data(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("装配API失败，apiId：{}，错误信息：{}", 
                    request != null ? request.getApiId() : "null", e.getMessage(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("装配失败：" + e.getMessage())
                    .data(false)
                    .build();
        }
    }

}
