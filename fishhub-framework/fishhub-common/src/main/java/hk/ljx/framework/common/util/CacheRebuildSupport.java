package hk.ljx.framework.common.util;

import java.util.function.Supplier;

/**
 * 缓存单飞重建：未命中时抢锁，抢到者重建并回写，未抢到者轮询等待，超时或锁不可用走兜底。
 */
public final class CacheRebuildSupport {

    private CacheRebuildSupport() {
    }

    /**
     * 读-重建。调用方需先确认缓存未命中。
     */
    public static <T> T getOrRebuild(RebuildLock lock, int pollTimes, long pollIntervalMillis,
                                     Supplier<T> cacheRead, Supplier<T> rebuild, Supplier<T> fallback) {
        boolean acquired;
        try {
            acquired = lock.tryLock();
        } catch (Exception ignored) {
            return fallback.get();
        }
        if (!acquired) {
            try {
                T waited = waitForValue(cacheRead, pollTimes, pollIntervalMillis);
                if (waited != null) {
                    return waited;
                }
            } catch (Exception ignored) {
                // 等待期间缓存不可读，按兜底处理
            }
            return fallback.get();
        }
        try {
            T reRead;
            try {
                reRead = cacheRead.get();
            } catch (Exception ignored) {
                return fallback.get();
            }
            if (reRead != null) {
                return reRead;
            }
            return rebuild.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 存在性重建：缓存不存在时抢锁重建，未抢到者轮询等待。
     */
    public static void rebuildIfMissing(RebuildLock lock, int pollTimes, long pollIntervalMillis,
                                        Supplier<Boolean> present, Runnable rebuild) {
        boolean acquired;
        try {
            acquired = lock.tryLock();
        } catch (Exception ignored) {
            return;
        }
        if (!acquired) {
            try {
                waitForPresent(present, pollTimes, pollIntervalMillis);
            } catch (Exception ignored) {
                // 等待期间缓存不可读，跳过
            }
            return;
        }
        try {
            if (Boolean.TRUE.equals(present.get())) {
                return;
            }
            rebuild.run();
        } finally {
            lock.unlock();
        }
    }

    private static <T> T waitForValue(Supplier<T> cacheRead, int pollTimes, long pollIntervalMillis) {
        for (int i = 0; i < pollTimes; i++) {
            sleep(pollIntervalMillis);
            T value = cacheRead.get();
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void waitForPresent(Supplier<Boolean> present, int pollTimes, long pollIntervalMillis) {
        for (int i = 0; i < pollTimes; i++) {
            sleep(pollIntervalMillis);
            if (Boolean.TRUE.equals(present.get())) {
                return;
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
