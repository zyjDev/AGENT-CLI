package cn.bugstack.ai.domain.agent.service.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * token 估算抽象
 * <p>
 * 用于在「模型调用之前」判断上下文是否超预算，这是本地估算不可替代的价值：
 * 模型返回的 usage 是事后数据，无法用来做调用前的门控。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/09/14
 */
public interface ITokenCounter {

    /**
     * 估算一段文本的 token 数（已应用校准系数）
     *
     * @param text 文本，可为 null
     * @return token 估算值，文本为空时返回 0
     */
    int estimate(String text);

    /**
     * 估算一组消息的 token 数（含每条消息的角色开销）
     *
     * @param messages 消息列表，可为 null
     * @return token 估算值
     */
    int estimateMessages(List<Message> messages);

    /**
     * 登记一次真实 usage 样本，用于修正估算偏差
     *
     * @param estimated         本次调用前本地估算出的 token
     * @param actualPromptTokens 模型返回的真实 prompt token
     */
    void calibrate(int estimated, int actualPromptTokens);

    /**
     * 当前校准系数：估算值 × 系数 ≈ 真实值
     *
     * @return 系数，样本不足时返回 1.0
     */
    double calibrationFactor();

}
