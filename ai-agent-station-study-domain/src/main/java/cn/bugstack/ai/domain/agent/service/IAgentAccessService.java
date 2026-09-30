package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentAccessVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;

import java.util.Collection;
import java.util.List;

/**
 * 智能体资源归属与可执行性校验 —— 领域服务。
 *
 * ============================ 为什么要有这一层 ============================
 * 「能不能用这个智能体」「自建智能体是不是借道了别人的 Key」「平台默认智能体是否已绑定自己的 Key」
 * 这三条都是<b>领域规则</b>，与 HTTP 无关。改造前它们写在 {@code AiAgentController} 里，
 * trigger 层因此直接注入了 3 个 infrastructure DAO（绕过领域层），
 * 且同一次请求里把同一份智能体数据查了 3 次 —— 都发生在流式对话的首包延迟路径上。
 *
 * 收敛到这里之后：
 * <ul>
 *   <li>trigger 只做参数装配与响应转换，换协议（如 gRPC）不必复制一遍规则；</li>
 *   <li>{@link #resolveAccess} 一次查询、一次返回，链路上不再重复查库；</li>
 *   <li>规则有了明确的落点，将来要加缓存 / 事件也有地方挂。</li>
 * </ul>
 * ======================================================================
 */
public interface IAgentAccessService {

    /**
     * 解析智能体的「可执行上下文」：一次查询，回答「能否使用 / 是否借道 / 是否已绑定」。
     *
     * <p>入参显式传入 {@code userId} 与 {@code admin}，而不是在领域层读线程上下文：
     * 归属判定全项目统一走显式传参（见 {@code OwnerScope} 注释），也更便于单测。
     *
     * @param agentId 智能体ID
     * @param userId  当前登录用户 id
     * @param admin   当前用户是否管理员（管理员不受「自带 Key / 必须绑定」两条限制）
     * @return 可执行上下文；{@code accessible=false} 表示无权使用
     */
    AiAgentAccessVO resolveAccess(String agentId, String userId, boolean admin);

    /**
     * 校验一组客户端是否都属于 {@code ownerId}（公共资源与别人的资源都算"借道"）。
     *
     * @return null = 通过；否则返回给用户看的提示
     */
    String checkClients(Collection<String> clientIds, String ownerId);

    /**
     * 校验一组模型是否都属于 {@code ownerId}（公共资源与别人的资源都算"借道"）。
     *
     * @return null = 通过；否则返回给用户看的提示
     */
    String checkModels(Collection<String> modelIds, String ownerId);

    /**
     * 取「某个用户自己绑定在该智能体上的」启用流程配置（只认他自己的绑定，不回落系统默认）。
     *
     * <p>用途：后端重启后用户级链路 Bean 会丢失，对话前据此判断是否需要补装配。
     */
    List<AiAgentClientFlowConfigVO> queryUserOwnFlowConfigs(String agentId, String userId);

    /**
     * 「装配 API 通道」的写权限校验：系统默认通道（owner 为空）仅管理员可装配，私有通道仅本人。
     *
     * @return true = 允许装配
     */
    boolean canWriteApiChannel(String apiId, String userId, boolean admin);

}
