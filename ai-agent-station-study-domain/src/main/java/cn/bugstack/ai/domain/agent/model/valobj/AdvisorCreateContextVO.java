package cn.bugstack.ai.domain.agent.model.valobj;

import cn.bugstack.ai.domain.agent.service.context.IContextSummarizer;
import cn.bugstack.ai.domain.agent.service.context.ITokenCounter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Advisor 创建期依赖包
 * <p>
 * 背景：{@code AiClientAdvisorTypeEnumVO.createAdvisor} 是枚举静态方法，拿不到 Spring Bean，
 * 而 Token 预算版的 ChatMemory 需要 token 计数器与摘要器。故把创建期所需依赖打包传入。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdvisorCreateContextVO {

    /**
     * 向量库（RagAnswer 顾问使用）
     */
    private VectorStore vectorStore;

    /**
     * token 估算器
     */
    private ITokenCounter tokenCounter;

    /**
     * 上下文摘要器
     */
    private IContextSummarizer contextSummarizer;

    /**
     * 全局默认预算；DB ext_param 可逐项覆盖
     */
    private ContextBudgetVO defaultBudget;

    /**
     * 精排用的 ChatModel（RagAnswer 顾问的可选增强）。
     * <p>
     * 由 {@code AiClientAdvisorNode} 按 ext_param 里的 {@code rerankModelBeanName}
     * 从容器取（模型节点先于 advisor 节点执行，Bean 已注册）；取不到时传 null，
     * 顾问侧自动降级为「不精排」，不影响装配。
     */
    private ChatModel rerankChatModel;

    /**
     * 多查询改写用的 ChatModel（RagAnswer 顾问的可选增强）。
     * <p>
     * 取用方式与 {@link #rerankChatModel} 一致：由 {@code AiClientAdvisorNode} 按 ext_param 里的
     * {@code multiQueryModelBeanName} 从容器取（模型节点先于 advisor 节点执行，Bean 已注册）；
     * 取不到时传 null，顾问侧自动降级为「单查询」，不影响装配。
     */
    private ChatModel expandChatModel;

}
