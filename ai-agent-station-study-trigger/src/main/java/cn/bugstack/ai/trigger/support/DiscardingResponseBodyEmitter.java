package cn.bugstack.ai.trigger.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 丢弃型 SSE sink —— 给「没有 HTTP 响应、只为复用 {@code dispatch(emitter)} 签名」的场景用。
 *
 * <p>唯一使用方：定时任务 {@code AgentTaskJob}。它复用 {@code IAgentDispatchService#dispatch}
 * 执行智能体，但这次执行没有任何 HTTP 观察者，而 {@code dispatch} 的签名要求一个
 * {@link ResponseBodyEmitter}。
 *
 * <h3>为什么不直接用 {@code new ResponseBodyEmitter()}</h3>
 * {@link ResponseBodyEmitter} 的设计前提是「由 Spring MVC 调用包级私有的 {@code initialize()}
 * 接管」。不交给 MVC 时 {@code handler} 恒为 null，{@code send(...)} 会把每次数据塞进内部
 * 无上限的 {@code earlySendAttempts} 集合（它只被 {@code initialize()} 清空，{@code complete()}
 * 不清空）—— 于是**一次定时任务的全部 SSE 事件（可能成百上千条、含完整回答文本）先被无上限地
 * 堆在堆内存里，任务结束后整体丢弃**，而这些输出又没有任何观察者，失败原因只能靠读代码推断。
 *
 * <h3>本类做的事</h3>
 * 复写全部 public 的 {@code send(...)} 重载，让事件**不进入任何缓冲**（直接丢弃），
 * 同时在日志里留痕：
 * <ul>
 *     <li>普通事件：{@code DEBUG} 级逐条打印（默认不打印，需要排查时再开）；</li>
 *     <li>错误事件（payload 里带 {@code "type":"error"}）：{@code WARN} 级打印，让定时任务
 *         的失败在默认日志级别下就可见；</li>
 *     <li>{@code complete()} 打一条 INFO 汇总（共丢弃多少条），{@code completeWithError(...)}
 *         打 WARN。</li>
 * </ul>
 *
 * <p><b>注意</b>：错误事件的识别基于 SSE payload 的文本特征（{@code AutoAgentExecuteResultEntity}
 * 的 {@code createErrorResult} 会把 {@code type} 置为 {@code "error"}）。这是有意为之的轻量做法 ——
 * 本类位于 trigger 层，不值得为一个日志增强去解析/耦合领域实体。
 */
@Slf4j
public class DiscardingResponseBodyEmitter extends ResponseBodyEmitter {

    /** 被丢弃的事件数：只做汇总计数，不保留任何事件内容，避免本类成为新的内存堆积点 */
    private final AtomicInteger discardedCount = new AtomicInteger();

    /** 日志前缀：用于区分是哪个定时任务（如 {@code task-123}） */
    private final String tag;

    public DiscardingResponseBodyEmitter(String tag) {
        this.tag = tag == null ? "-" : tag;
    }

    @Override
    public void send(Object object) {
        discard(object);
    }

    @Override
    public void send(Object object, MediaType mediaType) {
        discard(object);
    }

    @Override
    public void send(Set<DataWithMediaType> items) {
        if (items == null) {
            return;
        }
        for (DataWithMediaType item : items) {
            discard(item == null ? null : item.getData());
        }
    }

    @Override
    public void complete() {
        log.info("[{}] 丢弃型 SSE sink 结束：本次共丢弃 {} 条事件（无 HTTP 观察者）", tag, discardedCount.get());
    }

    @Override
    public void completeWithError(Throwable ex) {
        log.warn("[{}] 丢弃型 SSE sink 异常结束（已丢弃 {} 条事件）：{}",
                tag, discardedCount.get(), ex == null ? "null" : ex.toString());
    }

    private void discard(Object data) {
        discardedCount.incrementAndGet();
        if (data == null) {
            return;
        }
        String text = String.valueOf(data);
        // 错误事件在 WARN 留痕：定时任务没有观察者，否则「失败原因」只会躺在 DEBUG 里
        if (text.contains("\"type\":\"error\"")) {
            log.warn("[{}] 定时任务执行出错：{}", tag, text.trim());
        } else if (log.isDebugEnabled()) {
            log.debug("[{}] 丢弃事件：{}", tag, text.trim());
        }
    }

}
