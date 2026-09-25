package cn.bugstack.ai.domain.agent.service.execute.guard;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * 瞬时故障判定：决定「这次失败值不值得重试」
 * <p>
 * 判据必须是「会不会换个时机重来一次就可能成功」，而不是「异常看起来眼熟」。据此分三类：
 * <ol>
 *   <li><b>可重试</b>（ transport 层抖动）：连接超时、读超时、连接被重置、429 限流、5xx 服务端错误。</li>
 *   <li><b>不可重试</b>（本地参数/配置问题）：本项目大量使用 {@code BizException} 表达「缺 ai_agent_flow_config 配置」、
 *       IllegalArgumentException 表达「advisor 缺 conversationId」—— 重试一万次结果完全一样，只会浪费时间和 token。</li>
 *   <li><b>读超时</b>：请求可能已被服务端接收并开始计费。仍然允许重试（次数更少），
 *       因为在本链路里「拿不到结果」与「没花 token」相比前者代价更大，但要退避且仅一次。</li>
 * </ol>
 */
public final class RetryJudge {

    private RetryJudge() {
    }

    /** 明确的服务端状态码：429 限流 / 5xx 服务端故障 */
    private static final String[] RETRYABLE_STATUS_MARKS = {
            "429", "502", "503", "504",
            "too many requests", "service unavailable", "bad gateway", "gateway timeout"
    };

    public static boolean isTransient(Throwable t) {
        return isTransient(t, true);
    }

    /**
     * @param allowReadTimeout 是否把 SocketTimeoutException / TimeoutException 视为可重试
     */
    public static boolean isTransient(Throwable t, boolean allowReadTimeout) {
        if (t == null) {
            return false;
        }
        if (t instanceof TimeoutException || t instanceof SocketTimeoutException) {
            return allowReadTimeout;
        }
        if (t instanceof ConnectException || t instanceof SocketException) {
            return true;
        }

        Throwable cursor = t;
        for (int depth = 0; cursor != null && depth < 8; depth++) {
            String name = cursor.getClass().getName();
            // Spring 对 HTTP 调用失败的统一包装：ResourceAccessException / RestClientException / WebClientException
            if (name.startsWith("org.springframework.web.client.")
                    || name.startsWith("org.springframework.web.reactive.function.client.")
                    || name.startsWith("org.springframework.ai.retry.")) {
                // 落到下面的 message 判定：需要区分 4xx 业务错误与 5xx/429
                String message = cursor.getMessage();
                if (message != null && message.toLowerCase().contains("4")
                        && !isRetryableStatus(message)) {
                    // 普通 4xx（参数/权限）不重试
                    return false;
                }
                return true;
            }
            if (name.startsWith("java.net.") || name.startsWith("java.io.") || name.startsWith("javax.net.ssl.")) {
                return true;
            }
            cursor = cursor.getCause();
        }

        return isRetryableStatus(t.getMessage());
    }

    private static boolean isRetryableStatus(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        for (String mark : RETRYABLE_STATUS_MARKS) {
            if (lower.contains(mark)) {
                return true;
            }
        }
        return lower.contains("timeout") || lower.contains("timed out") || lower.contains("connection reset");
    }
}
