package cn.bugstack.ai.trigger.support;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密码校验入口的限流与临时锁定（防暴力破解 / 防批量注册）。
 *
 * ============================ 为什么需要它 ============================
 * 三处「用密码换东西」的入口此前都是可以无限次尝试的：
 * <ul>
 *   <li>{@code /api/v1/admin/admin-user/login} —— 换 token；</li>
 *   <li>{@code /api/v1/admin/admin-user/validate-login} —— <b>同样校验密码</b>，
 *       且被 {@code AdminWebConfig} 显式放行为匿名接口。只堵 login 不堵它，等于没堵；</li>
 *   <li>{@code /api/v1/admin/admin-user/change-password} —— 校验原密码（token 泄露后的二次爆破面）。</li>
 * </ul>
 * 注册 {@code /register} 也是匿名的：无验证码、无频率限制，脚本可以无限开号。
 *
 * ============================ 口径 ============================
 * <ul>
 *   <li><b>账号维度</b>：同一账号连续失败 {@code max-failures} 次 → 临时锁定 {@code lock-minutes} 分钟。
 *       登录成功即清零。失败计数有 {@code failure-window-minutes} 的记忆窗口，
 *       避免「很久以前的几次失败 + 今天的失败」被误判成连续爆破。</li>
 *   <li><b>不存在的账号同样计数</b>：如果只给存在的账号计数，
 *       「你已被锁定」就变成了账号存在性的探测信号，反而新增一个枚举面。</li>
 *   <li><b>注册按来源 IP</b> 做滑动窗口限流（只计<b>成功</b>创建，失败的重试不吃配额）。</li>
 * </ul>
 *
 * ============================ 明确的边界（不假装解决）============================
 * 1. <b>状态在进程内</b>（ConcurrentHashMap）：重启即清空、多实例各算一份。
 *    当前项目只能是单实例部署（装配产物与对话记忆都在进程内，见
 *    {@code AbstractArmorySupport} / {@code AiClientAdvisorTypeEnumVO}），
 *    所以这与部署形态一致；将来上多实例要连同这些一起换成共享存储。
 * 2. <b>按账号锁定会让攻击者「锁定别人的账号」</b>（DoS 面）：这是锁定策略的固有代价，
 *    因此锁定时长取短（默认 15 分钟）且只锁密码校验入口，不影响已登录的会话。
 * 3. <b>不防「密码喷洒」</b>（一个口令试一万个账号）：那需要按来源 IP 统计失败，
 *    而 {@code getRemoteAddr()} 在反向代理后面是代理自己的地址，
 *    按它统计会把所有人都锁住。这一层应由网关限流兜底，见下面 {@link #clientIp()} 的说明。
 * ==================================================================================
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    /** 同一账号连续失败多少次后临时锁定；{@code <=0} = 关闭锁定 */
    @Value("${xfg.ai.auth.login.max-failures:5}")
    private int maxFailures = 5;

    /** 临时锁定时长（分钟） */
    @Value("${xfg.ai.auth.login.lock-minutes:15}")
    private int lockMinutes = 15;

    /**
     * 失败计数的记忆窗口（分钟）：距上次失败超过该时长，之前的失败次数不再累加。
     */
    @Value("${xfg.ai.auth.login.failure-window-minutes:15}")
    private int failureWindowMinutes = 15;

    /** 注册限流：同一来源 IP 在窗口内最多成功注册多少个账号；{@code <=0} = 关闭限流 */
    @Value("${xfg.ai.auth.register.max-per-window:20}")
    private int registerMaxPerWindow = 20;

    /** 注册限流窗口（分钟） */
    @Value("${xfg.ai.auth.register.window-minutes:60}")
    private int registerWindowMinutes = 60;

    /**
     * 计数表的硬上限（防「攻击者用海量随机用户名把内存撑爆」）。
     * 超限时先清理过期项，仍然超限就整体清空 ——
     * 限流只负责「延缓爆破」，不是访问控制，清空不会造成越权。
     */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, FailureRecord> failures = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> registerWindow = new ConcurrentHashMap<>();

    /**
     * 账号维度的 key（登录入口）。
     * <p>
     * 统一 trim + 小写：否则换一下大小写就能换一个计数器，限流等于形同虚设。
     */
    public static String accountKey(String username) {
        return "account:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    /** 用户维度 key（改密码入口）。与 {@link #accountKey} 前缀不同，不会互相干扰 */
    public static String userKey(String userId) {
        return "user:" + (userId == null ? "" : userId);
    }

    /**
     * 该账号距离解锁还有多少秒。
     *
     * @return {@code 0} = 未锁定，可以继续校验密码
     */
    public long lockedSecondsRemaining(String key) {
        FailureRecord record = failures.get(key);
        if (record == null) {
            return 0;
        }
        long remaining = record.lockedUntilMillis() - System.currentTimeMillis();
        return remaining > 0 ? (remaining + 999) / 1000 : 0;
    }

    /** 记一次密码校验失败；达到阈值时顺带把账号锁上 */
    public void recordFailure(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        if (maxFailures <= 0) {
            // max-failures <= 0 = 关闭锁定（也顺带不做无意义的计数，省内存）
            return;
        }
        long now = System.currentTimeMillis();
        pruneFailuresIfNeeded(now);
        failures.compute(key, (k, record) -> {
            if (record == null || now - record.lastFailureMillis() > minutesToMillis(failureWindowMinutes)) {
                return new FailureRecord(1, now, 0);
            }
            int count = record.failures() + 1;
            if (count < maxFailures) {
                return new FailureRecord(count, now, 0);
            }
            log.warn("连续密码校验失败 {} 次，临时锁定 {} 分钟：key={}", count, lockMinutes, k);
            return new FailureRecord(count, now, now + minutesToMillis(lockMinutes));
        });
    }

    /** 密码校验成功：清掉该账号的失败计数 */
    public void reset(String key) {
        failures.remove(key);
    }

    /**
     * 当前来源还能不能注册。
     *
     * @return {@code 0} = 可以注册；{@code >0} = 需要等待的秒数
     */
    public long registerBlockedSeconds() {
        if (registerMaxPerWindow <= 0) {
            // max-per-window <= 0 = 关闭注册限流
            return 0;
        }
        String ip = clientIp();
        Deque<Long> timestamps = registerWindow.get(ip);
        if (timestamps == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long windowMillis = minutesToMillis(registerWindowMinutes);
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() < registerMaxPerWindow) {
                return 0;
            }
            Long oldest = timestamps.peekFirst();
            if (oldest == null) {
                return 0;
            }
            return Math.max(1, (oldest + windowMillis - now + 999) / 1000);
        }
    }

    /** 注册成功后记一笔（只计成功，重试已存在用户名不吃配额） */
    public void recordRegister() {
        String ip = clientIp();
        long now = System.currentTimeMillis();
        pruneRegisterWindowIfNeeded(now);
        Deque<Long> timestamps = registerWindow.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(now);
        }
    }

    /**
     * 取来源 IP。
     * <p>
     * 有 {@code X-Forwarded-For} 时取第一跳（反向代理会追加），否则用 socket 地址。
     * <b>注意</b>：应用若被直连暴露，XFF 是客户端可伪造的（只能绕过限流，不是新的漏洞）；
     * 生产建议在网关层再做一层限流，并让 Spring 按
     * {@code server.forward-headers-strategy=framework} 统一处理代理头。
     */
    public String clientIp() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (StringUtils.hasText(forwarded)) {
                    int comma = forwarded.indexOf(',');
                    String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
                    if (StringUtils.hasText(first)) {
                        return first.trim();
                    }
                }
                String remoteAddr = request.getRemoteAddr();
                if (StringUtils.hasText(remoteAddr)) {
                    return remoteAddr;
                }
            }
        } catch (Exception e) {
            // 非 Web 上下文（定时任务 / 单测）取不到请求，退化成同一个桶即可
            log.debug("取来源 IP 失败，按 unknown 处理：{}", e.getMessage());
        }
        return "unknown";
    }

    private void pruneFailuresIfNeeded(long now) {
        if (failures.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        long staleBefore = now - minutesToMillis(Math.max(failureWindowMinutes, lockMinutes));
        failures.entrySet().removeIf(entry -> {
            FailureRecord record = entry.getValue();
            return record.lockedUntilMillis() <= now && record.lastFailureMillis() < staleBefore;
        });
        if (failures.size() > MAX_TRACKED_KEYS) {
            log.warn("登录失败计数表超过 {} 条，整体清理（限流非访问控制，清理不影响鉴权正确性）", MAX_TRACKED_KEYS);
            failures.clear();
        }
    }

    private void pruneRegisterWindowIfNeeded(long now) {
        if (registerWindow.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        long windowMillis = minutesToMillis(registerWindowMinutes);
        registerWindow.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
        if (registerWindow.size() > MAX_TRACKED_KEYS) {
            log.warn("注册限流表超过 {} 条，整体清理", MAX_TRACKED_KEYS);
            registerWindow.clear();
        }
    }

    private long minutesToMillis(int minutes) {
        return minutes * 60_000L;
    }

    /** 账号的失败计数快照（不可变） */
    private record FailureRecord(int failures, long lastFailureMillis, long lockedUntilMillis) {
    }

}
