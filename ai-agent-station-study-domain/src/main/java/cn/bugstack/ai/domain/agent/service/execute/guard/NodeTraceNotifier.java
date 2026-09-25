package cn.bugstack.ai.domain.agent.service.execute.guard;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 节点级 SSE 事件推送
 * <p>
 * 原实现的痛点是「一个节点内部对用户是全黑屏」：节点结束才 push 一次，
 * 慢节点期间用户既不知道在做什么、也不知道是不是卡住了。这里补齐三类事件：
 * <ul>
 *   <li>node_start：节点开始，前端可展示「规划中…」</li>
 *   <li>node_retry：重试，说明此刻正在等待模型，而不是死等</li>
 *   <li>node_degrade / node_timeout / node_budget：降级原因，回答「结果为什么是兜底内容」</li>
 * </ul>
 * type 统一为 {@code progress}，未适配的前端按未知类型忽略即可，不影响既有的 analysis/execution 渲染。
 */
@Slf4j
public final class NodeTraceNotifier {

    private NodeTraceNotifier() {
    }

    public static final String PHASE_START = "node_start";
    public static final String PHASE_END = "node_end";
    public static final String PHASE_RETRY = "node_retry";
    public static final String PHASE_TIMEOUT = "node_timeout";
    public static final String PHASE_DEGRADE = "node_degrade";
    public static final String PHASE_BUDGET = "node_budget";

    public static void notifyEvent(ResponseBodyEmitter emitter, AutoAgentExecuteResultEntity result) {
        if (emitter == null || result == null) {
            return;
        }
        try {
            emitter.send("data: " + JSON.toJSONString(result) + "\n\n");
        } catch (IllegalStateException ignore) {
            // emitter 已被完成/超时（客户端断开），继续写毫无意义
        } catch (Exception e) {
            log.warn("节点事件推送失败：subType={}, error={}", result.getSubType(), e.getMessage());
        }
    }

    /**
     * @param displayName 节点展示名，用于拼接可读文案；可为 null
     */
    public static void send(ResponseBodyEmitter emitter, String sessionId, Integer step,
                            String displayName, String phase, String detail) {
        if (emitter == null) {
            return;
        }
        String nodeName = displayName == null ? "-" : displayName;
        String content = detail == null ? nodeName
                : String.format("[%s] %s", nodeName, detail);
        notifyEvent(emitter, AutoAgentExecuteResultEntity.createProgressSubResult(
                step, phase, content, sessionId));
    }
}
