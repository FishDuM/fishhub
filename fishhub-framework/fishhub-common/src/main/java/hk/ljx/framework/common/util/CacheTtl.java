package hk.ljx.framework.common.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存过期时间工具：统一"保底时长 + 随机抖动"的打散写法，
 * 避免各处复制 60*60 + RandomUtil.randomInt(...) 类算式。
 */
public final class CacheTtl {

    private CacheTtl() {
    }

    /** baseSeconds + [0, jitterSeconds) 的随机抖动，单位秒 */
    public static long basePlusRandom(long baseSeconds, long jitterSeconds) {
        return Math.max(1L, baseSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds));
    }

    public static long minutes(int baseMinutes, int jitterMinutes) {
        return basePlusRandom(baseMinutes * 60L, jitterMinutes * 60L);
    }

    public static long hours(int baseHours, int jitterHours) {
        return basePlusRandom(baseHours * 3600L, jitterHours * 3600L);
    }

    public static long days(int baseDays, int jitterDays) {
        return basePlusRandom(baseDays * 86400L, jitterDays * 86400L);
    }
}
