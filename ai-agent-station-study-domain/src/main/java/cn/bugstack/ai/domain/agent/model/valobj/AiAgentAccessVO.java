package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体「可执行上下文」：一次解析，回答三个问题。
 *
 * <ol>
 *   <li>{@link #accessible} —— 这个智能体对当前用户是否可见可用（平台默认 + 本人私有）；</li>
 *   <li>{@link #ownModelProblem} —— 若它是「他自己建的」，链路上的客户端/模型是否都是他自己的；</li>
 *   <li>{@link #bindingProblem} —— 若它是「平台默认的」，他是否已绑定自己的模型 Key。</li>
 * </ol>
 *
 * <p>这三个结论共用<b>同一次</b>智能体归属查询，因此打包成一个 VO 返回。
 * 改造前它们在 HTTP 层各自独立触发一次 {@code queryByAgentId}，同一份数据在一次请求里被查了 3 次。
 *
 * @author bugstack虫洞栈
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiAgentAccessVO {

    /**
     * 智能体是否存在且对当前用户可见可用（平台默认资源 or 本人私有）。
     * false 时另外两个字段无意义。
     */
    private boolean accessible;

    /**
     * 「普通用户自建智能体必须自带模型 Key」校验未通过时的提示；null = 通过。
     */
    private String ownModelProblem;

    /**
     * 「平台默认智能体必须绑定自己的 Key」校验未通过时的提示；null = 通过。
     */
    private String bindingProblem;

}
