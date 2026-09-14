package cn.bugstack.ai.domain.agent.service.context;

import cn.bugstack.ai.domain.agent.model.valobj.ContextBudgetVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 jtokkit 的 token 估算实现
 * <p>
 * 注意：jtokkit 使用 OpenAI 的 BPE 编码，本项目实际模型为 OpenAI 兼容接口下的小米 MiMo，
 * 两者分词规则不同，因此估算值通过 EMA 校准系数向真实 usage 逼近。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
@Slf4j
@Service
public class JTokkitTokenCounter implements ITokenCounter {

    /**
     * 每条消息的固定角色开销（role 标记 + 分隔符）
     */
    private static final int PER_MESSAGE_OVERHEAD = 4;

    /**
     * 校准系数夹紧范围，防止个别异常样本把系数带飞
     */
    private static final double MIN_FACTOR = 0.5d;

    private static final double MAX_FACTOR = 2.0d;

    private final TokenCountEstimator estimator = new JTokkitTokenCountEstimator();

    /**
     * 校准系数，默认 1.0（纯估算）
     */
    private final AtomicReference<Double> factor = new AtomicReference<>(1.0d);

    private final AtomicInteger samples = new AtomicInteger(0);

    @Resource
    private ContextBudgetVO contextBudget;

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int raw;
        try {
            raw = estimator.estimate(text);
        } catch (Exception e) {
            // jtokkit 对极端输入可能抛异常，退化为「字符数 / 2」的粗估
            raw = Math.max(1, text.length() / 2);
        }
        return Math.max(1, (int) Math.round(raw * factor.get()));
    }

    @Override
    public int estimateMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message message : messages) {
            total += PER_MESSAGE_OVERHEAD + estimate(safeText(message));
        }
        return total;
    }

    /**
     * 安全取消息文本。
     * <p>
     * 坑：ToolResponseMessage 等消息 getText() 可能为 null，而
     * {@code JTokkitTokenCountEstimator.estimate(MediaContent)} 内部会直接 countTokens(text)，
     * 传 null 会 NPE。此处必须三级兜底。
     */
    private String safeText(Message message) {
        if (message == null) {
            return "";
        }
        try {
            String text = message.getText();
            if (text != null) {
                return text;
            }
        } catch (Exception ignore) {
            // 落到下面的序列化兜底
        }
        try {
            return String.valueOf(message);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void calibrate(int estimated, int actualPromptTokens) {
        if (!contextBudget.isCalibrationEnabled()) {
            return;
        }
        if (estimated <= 0 || actualPromptTokens <= 0) {
            return;
        }

        double observed = (double) actualPromptTokens / (double) estimated;
        if (observed < MIN_FACTOR || observed > MAX_FACTOR) {
            log.debug("token 校准样本超出夹紧范围，丢弃：estimated={}, actual={}", estimated, actualPromptTokens);
            return;
        }

        int count = samples.incrementAndGet();
        // 样本不足时先不动系数，避免被首个异常样本带偏
        if (count < contextBudget.getCalibrationMinSamples()) {
            return;
        }

        double alpha = contextBudget.getCalibrationAlpha();
        double updated = factor.updateAndGet(prev -> {
            // ⚠️ 必须在「乘性空间」做 EMA：estimated 本身已乘过 prev，故
            //    observed = actual / (raw × prev) = f* / prev。
            //    若用线性 EMA：f' = (1-α)f + α(f*/f)，不动点是 f = √f*（系数会系统性偏小，例如
            //    真实系数为 2 时只收敛到 1.414）。改用 f' = f × observed^α，不动点恰好是 f*。
            double next = prev * Math.pow(observed, alpha);
            return Math.min(MAX_FACTOR, Math.max(MIN_FACTOR, next));
        });

        log.info("token 估算校准：estimated={}, actual={}, observed={}, factor={}, samples={}",
                estimated, actualPromptTokens, String.format("%.3f", observed),
                String.format("%.3f", updated), count);
    }

    @Override
    public double calibrationFactor() {
        return factor.get();
    }

}
