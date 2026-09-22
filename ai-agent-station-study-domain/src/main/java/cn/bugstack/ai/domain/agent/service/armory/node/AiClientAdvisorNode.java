package cn.bugstack.ai.domain.agent.service.armory.node;

import cn.bugstack.ai.domain.agent.model.entity.ArmoryCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientAdvisorTypeEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.AdvisorCreateContextVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientModelVO;
import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import cn.bugstack.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.ai.domain.agent.service.context.IContextSummarizer;
import cn.bugstack.ai.domain.agent.service.context.ITokenCounter;
import cn.bugstack.ai.domain.agent.service.support.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 顾问角色节点
 *
 * 2025/7/19 08:51
 */
@Slf4j
@Service
public class AiClientAdvisorNode extends AbstractArmorySupport {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private AiClientNode aiClientNode;

    @Resource
    private ITokenCounter tokenCounter;

    @Resource
    private IContextSummarizer contextSummarizer;

    @Resource
    private ContextBudgetVO contextBudget;

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建节点，Advisor 顾问角色{}", JSON.toJSONString(requestParameter));

        List<AiClientAdvisorVO> aiClientAdvisorList = dynamicContext.getValue(dataName());

        if (aiClientAdvisorList == null || aiClientAdvisorList.isEmpty()) {
            log.warn("没有需要被初始化的 ai client advisor");
            return router(requestParameter, dynamicContext);
        }

        for (AiClientAdvisorVO aiClientAdvisorVO : aiClientAdvisorList) {
            // 构建顾问访问对象
            Advisor advisor = createAdvisor(aiClientAdvisorVO, dynamicContext);
            // 注册Bean对象
            registerBean(beanName(aiClientAdvisorVO.getAdvisorId()), Advisor.class, advisor);
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientNode;
    }

    protected String beanName(String beanId) {
        return AiAgentEnumVO.AI_CLIENT_ADVISOR.getBeanName(beanId);
    }

    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT_ADVISOR.getDataName();
    }

    private Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO,
                                  DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        String advisorType = aiClientAdvisorVO.getAdvisorType();
        AiClientAdvisorTypeEnumVO advisorTypeEnum = AiClientAdvisorTypeEnumVO.getByCode(advisorType);
        // 枚举静态方法拿不到 Spring Bean，故把创建期依赖打包传入
        AdvisorCreateContextVO createContext = AdvisorCreateContextVO.builder()
                .vectorStore(vectorStore)
                .tokenCounter(tokenCounter)
                .contextSummarizer(contextSummarizer)
                .defaultBudget(contextBudget)
                .rerankChatModel(resolveRerankChatModel(aiClientAdvisorVO, dynamicContext))
                .expandChatModel(resolveExpandChatModel(aiClientAdvisorVO, dynamicContext))
                .build();
        return advisorTypeEnum.createAdvisor(aiClientAdvisorVO, createContext);
    }

    /**
     * 解析精排用的 ChatModel。
     * <p>
     * <b>刻意不复用对话模型 Bean，而是单独构建一个实例</b>，原因有两个：
     * <ol>
     *     <li>对话模型 Bean 上挂了 {@code toolCallbacks}（MCP 工具），精排只需要一次纯文本打分调用，
     *         带着工具定义既浪费 token 又可能被模型误触发；</li>
     *     <li>精排需要不同的采样参数：{@code temperature=0} 保证同一份候选稳定重排（评测可复现），
     *         以及 {@code reasoning_effort=none} 关掉推理（见下）。</li>
     * </ol>
     * <b>为什么必须关推理</b>：默认模型 mimo-v2.5 是推理模型。实测 20 候选的 listwise prompt
     * 在推理模式下单次耗时 29.8s，且 {@code finish_reason=length}、content 为空 —— 输出预算
     * 全被推理 token 吃光，精排永远拿不到分数。关掉后降到约 7~10s 并能正常返回 JSON。
     * <p>
     * <b>解析失败一律返回 null</b>：精排是可选增强，Bean 名写错 / 模型未装配都只应
     * 「静默降级为不精排」，绝不能让装配期抛异常 —— 那会让整个应用启动失败。
     */
    @SuppressWarnings("unchecked")
    private ChatModel resolveRerankChatModel(AiClientAdvisorVO aiClientAdvisorVO,
                                            DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        AiClientAdvisorVO.RagAnswer cfg = aiClientAdvisorVO.getRagAnswer();
        if (cfg == null || !cfg.rerankActive()) {
            return null;
        }
        String beanName = cfg.getRerankModelBeanName();
        try {
            ChatModel dedicated = buildDedicatedRerankModel(cfg, dynamicContext);
            if (dedicated != null) {
                log.info("精排模型已构建（独立实例）：beanName={}, reasoningEffort={}, temperature=0",
                        beanName, cfg.getRerankReasoningEffort());
                return dedicated;
            }
            // 兜底：拿不到模型元信息时退回共享的对话模型 Bean（行为等价于改造前，只是参数不可控）
            log.warn("未能在装配上下文中定位精排模型元信息，退回共享模型 Bean：{}", beanName);
            return this.<ChatModel>getBean(beanName);
        } catch (Exception e) {
            log.warn("精排模型 Bean 解析失败，该知识库顾问降级为不精排。advisorId={}, beanName={}, err={}",
                    aiClientAdvisorVO.getAdvisorId(), beanName, e.toString());
            return null;
        }
    }

    /**
     * 解析多查询改写用的 ChatModel。
     * <p>
     * 与 {@link #resolveRerankChatModel} 完全同构：单独构建实例（不带 toolCallbacks、独立采样参数），
     * 解析失败一律返回 null → 顾问侧降级为「单查询」。装配期绝不抛异常，否则整个应用启动会失败。
     */
    @SuppressWarnings("unchecked")
    private ChatModel resolveExpandChatModel(AiClientAdvisorVO aiClientAdvisorVO,
                                             DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        AiClientAdvisorVO.RagAnswer cfg = aiClientAdvisorVO.getRagAnswer();
        if (cfg == null || !cfg.multiQueryActive()) {
            return null;
        }
        String beanName = cfg.getMultiQueryModelBeanName();
        try {
            ChatModel dedicated = buildDedicatedExpandModel(cfg, dynamicContext);
            if (dedicated != null) {
                log.info("多查询改写模型已构建（独立实例）：beanName={}, temperature=0.3, reasoningEffort=none",
                        beanName);
                return dedicated;
            }
            log.warn("未能在装配上下文中定位多查询改写模型元信息，退回共享模型 Bean：{}", beanName);
            return this.<ChatModel>getBean(beanName);
        } catch (Exception e) {
            log.warn("多查询改写模型 Bean 解析失败，该知识库顾问降级为单查询。advisorId={}, beanName={}, err={}",
                    aiClientAdvisorVO.getAdvisorId(), beanName, e.toString());
            return null;
        }
    }

    /** 精排专用模型：判定任务，temperature=0 保证同一份候选稳定重排 */
    private ChatModel buildDedicatedRerankModel(AiClientAdvisorVO.RagAnswer cfg,
                                                DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        return buildDedicatedChatModel(cfg.getRerankModelBeanName(),
                0.0,
                cfg.getRerankReasoningEffort(),
                dynamicContext);
    }

    /**
     * 多查询改写专用模型。
     * <p>
     * temperature 给 0.3 而不是 0：改写要产出「不同角度」的变体，完全确定性会让变体趋同、
     * 失去扩召回的意义；但也不能高，否则变体会偏离原问题意图。
     * <p>
     * <b>reasoningEffort 强制 "none"</b>：默认模型 mimo-v2.5 是推理模型，开着推理时输出预算会被
     * 思考 token 吃光（精排已踩过同一个坑：29.8s、finish_reason=length、content 为空 →
     * 拿不到变体 → 静默降级，看起来「跑了但没效果」）。
     */
    private ChatModel buildDedicatedExpandModel(AiClientAdvisorVO.RagAnswer cfg,
                                                DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        return buildDedicatedChatModel(cfg.getMultiQueryModelBeanName(),
                0.3,
                "none",
                dynamicContext);
    }

    /**
     * 按「模型元信息 + API Bean」构建一个专用 ChatModel 实例。
     * <p>
     * 模型数据由 {@code AiClientLoadDataStrategy} 在整条装配链开始前一次性写入 DynamicContext，
     * 所以这里直接读得到，不需要额外查库。
     *
     * @param beanName        模型 Bean 名（如 ai_client_model_3001）
     * @param temperature     采样温度
     * @param reasoningEffort 推理强度；null / 空白表示沿用模型默认
     * @return 构建好的模型；模型元信息或 API Bean 缺失时返回 null（由调用方走兜底）
     */
    private ChatModel buildDedicatedChatModel(String beanName,
                                              double temperature,
                                              String reasoningEffort,
                                              DefaultArmoryStrategyFactory.DynamicContext dynamicContext) {
        Object raw = dynamicContext.getValue(AiAgentEnumVO.AI_CLIENT_MODEL.getDataName());
        if (!(raw instanceof List<?> modelList)) {
            return null;
        }
        for (Object item : modelList) {
            if (!(item instanceof AiClientModelVO modelVO)) {
                continue;
            }
            if (!beanName.equals(AiAgentEnumVO.AI_CLIENT_MODEL.getBeanName(modelVO.getModelId()))) {
                continue;
            }
            OpenAiApi openAiApi = getBean(AiAgentEnumVO.AI_CLIENT_API.getBeanName(modelVO.getApiId()));
            if (openAiApi == null) {
                return null;
            }
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                    .model(modelVO.getModelName())
                    .temperature(temperature);
            if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                options.reasoningEffort(reasoningEffort.trim());
            }
            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options.build())
                    .build();
        }
        return null;
    }

}
