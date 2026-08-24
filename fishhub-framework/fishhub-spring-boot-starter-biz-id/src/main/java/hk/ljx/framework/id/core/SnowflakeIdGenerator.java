package hk.ljx.framework.id.core;

import cn.hutool.core.net.NetUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

/**
 * 高性能企业级雪花算法发号器：
 * 结构：1位符号位(0) + 41位时间戳(约69年) + 5位机房ID(0~31) + 5位机器ID(0~31) + 12位序列号(0~4095/ms)
 * 特性：
 * 1. 自动基于机器 IP 计算 workerId（也可通过配置指定）；
 * 2. 毫秒内起始序列号随机抖动（0~9），防止小并发下 ID 末位全为偶数/全为0的倾斜问题；
 * 3. 具备时钟回拨容忍保护机制（<=5ms 自动自旋等待追平，>5ms 抛出异常防护）。
 */
@Slf4j
public class SnowflakeIdGenerator {

    /**
     * 起始时间戳 (2024-01-01 00:00:00 UTC)
     */
    private static final long START_TIMESTAMP = 1704067200000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final Random RANDOM = new Random();

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(Long customWorkerId, Long customDatacenterId) {
        if (customDatacenterId != null && (customDatacenterId < 0 || customDatacenterId > MAX_DATACENTER_ID)) {
            throw new IllegalArgumentException(String.format("datacenterId must between 0 and %d", MAX_DATACENTER_ID));
        }
        this.datacenterId = customDatacenterId != null ? customDatacenterId : 1L;

        if (customWorkerId != null) {
            if (customWorkerId < 0 || customWorkerId > MAX_WORKER_ID) {
                throw new IllegalArgumentException(String.format("workerId must between 0 and %d", MAX_WORKER_ID));
            }
            this.workerId = customWorkerId;
        } else {
            this.workerId = autoGenerateWorkerId();
        }
        log.info("SnowflakeIdGenerator 初始化成功: datacenterId={}, workerId={}", this.datacenterId, this.workerId);
    }

    /**
     * 生成下一个全局唯一 ID（线程安全）
     */
    public synchronized long nextId() {
        long currentTimestamp = timeGen();

        // 时钟回拨判定
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                // 小于等于 5ms，尝试等待追平时钟
                try {
                    wait(offset << 1);
                    currentTimestamp = timeGen();
                    if (currentTimestamp < lastTimestamp) {
                        throw new IllegalStateException(String.format("时钟回拨等待追平失败: offset=%dms", offset));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("时钟回拨等待中断", e);
                }
            } else {
                throw new IllegalStateException(String.format("系统时钟回拨超过阈值: offset=%dms, 拒绝发号", offset));
            }
        }

        if (lastTimestamp == currentTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 毫秒内序列溢出，等待下一毫秒
                currentTimestamp = tilNextMillis(lastTimestamp);
                sequence = RANDOM.nextInt(10);
            }
        } else {
            // 新的毫秒，序列号随机抖动起始（0~9）
            sequence = RANDOM.nextInt(10);
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 基于本机 IP 计算 0~31 之间的 workerId
     */
    private long autoGenerateWorkerId() {
        try {
            String localhost = NetUtil.getLocalhostStr();
            byte[] bytes = localhost.getBytes();
            int sum = 0;
            for (byte b : bytes) {
                sum += b & 0xFF;
            }
            return (sum % (MAX_WORKER_ID + 1));
        } catch (Exception e) {
            log.warn("自动获取 workerId 异常，回退为随机值", e);
            return RANDOM.nextInt((int) MAX_WORKER_ID + 1);
        }
    }
}
