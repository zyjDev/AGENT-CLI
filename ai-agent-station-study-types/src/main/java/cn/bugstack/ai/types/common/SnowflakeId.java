package cn.bugstack.ai.types.common;

import java.net.InetAddress;

/**
 * 雪花 ID 生成器：41 位时间戳 + 10 位机器位 + 12 位序列，产出 19 位纯数字。
 *
 * ============================ 为什么放在 types ============================
 * MyBatis-Plus 虽然自带 {@code IdWorker}，但它只在 infrastructure 模块的依赖里，
 * 而 ragId 是在 domain 层生成的 —— 为了让 domain / trigger / app 用同一套 ID 规则，
 * 这里在 types（所有模块都依赖的公共层）实现一份。
 *
 * 纪元刻意对齐 MyBatis-Plus 的 IdWorker（2010-11-04），前端 CrudPage 也按同一布局生成，
 * 因此三方产出的都是 19 位纯数字、量级一致，排查问题时一眼能认出是雪花 ID。
 * ==========================================================================
 *
 * 说明：
 * <ul>
 *   <li>方法为 {@code synchronized}，同进程内线程安全；</li>
 *   <li>时钟回拨时退化为"沿用上一毫秒"，宁可短暂重复等待也不产生重复 ID；</li>
 *   <li>机器位取主机名哈希低 10 位：单机部署恒定，多实例也能大概率错开。</li>
 * </ul>
 */
public final class SnowflakeId {

    /** 起始纪元，与 MyBatis-Plus IdWorker 保持一致 */
    private static final long EPOCH = 1288834974657L;

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private static final long WORKER_ID = resolveWorkerId();

    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private SnowflakeId() {
    }

    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            // 时钟回拨：不抛异常、不生成重复值，沿上一毫秒继续排序列号
            timestamp = lastTimestamp;
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (WORKER_ID << WORKER_ID_SHIFT) | sequence;
    }

    /** 字符串形态：业务 ID 列都是 varchar，直接落库 */
    public static String nextIdStr() {
        return Long.toString(nextId());
    }

    private static long waitNextMillis(long last) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= last) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    private static long resolveWorkerId() {
        try {
            return Math.floorMod(InetAddress.getLocalHost().getHostName().hashCode(), (int) (1L << WORKER_ID_BITS));
        } catch (Exception e) {
            return 1L;
        }
    }
}
