package cn.bugstack.ai.domain.agent.service.execute.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 心跳
 * <p>
 * 目的：节点慢但没超时时，连接上长时间没有数据，容易被 Nginx / 网关 / 浏览器按 idle 超时掐断。
 * 心跳发的是 SSE 注释行 {@code ": ping\n\n"} —— 不是 data 行，不会干扰前端按 type 分发的业务事件。
 */
@Slf4j
public class SseHeartbeat {

    private final ResponseBodyEmitter emitter;

    private ScheduledFuture<?> future;

    public SseHeartbeat(ResponseBodyEmitter emitter) {
        this.emitter = emitter;
    }

    /**
     * @param scheduler 心跳调度线程池（app 层 nodeHeartbeatScheduler）
     * @return 已启动的心跳，用完后必须 {@link #stop()}
     */
    public static SseHeartbeat start(ScheduledExecutorService scheduler, ResponseBodyEmitter emitter,
                                     long intervalMs, long nodeTimeoutMs) {
        if (scheduler == null || emitter == null || intervalMs <= 0 || nodeTimeoutMs < intervalMs) {
            return null;
        }
        SseHeartbeat heartbeat = new SseHeartbeat(emitter);
        heartbeat.future = scheduler.scheduleAtFixedRate(heartbeat::ping,
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        return heartbeat;
    }

    private void ping() {
        try {
            emitter.send(": ping\n\n");
        } catch (IllegalStateException ignore) {
            stopSilently();
        } catch (Exception e) {
            log.warn("SSE 心跳发送失败：{}", e.getMessage());
            stopSilently();
        }
    }

    public void stop() {
        if (future != null) {
            future.cancel(false);
        }
    }

    private void stopSilently() {
        try {
            stop();
        } catch (Exception ignore) {
            // 关闭阶段不再抛错
        }
    }
}
