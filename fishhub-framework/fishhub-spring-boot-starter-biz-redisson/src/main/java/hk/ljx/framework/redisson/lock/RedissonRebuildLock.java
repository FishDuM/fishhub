package hk.ljx.framework.redisson.lock;

import hk.ljx.framework.common.util.RebuildLock;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson RLock 的通用缓存单飞重建锁实现
 */
@Slf4j
public class RedissonRebuildLock implements RebuildLock {

    private final RedissonClient redissonClient;
    private final String lockKey;
    private final long leaseTimeSeconds;

    public RedissonRebuildLock(RedissonClient redissonClient, String lockKey, long leaseTimeSeconds) {
        this.redissonClient = redissonClient;
        this.lockKey = lockKey;
        this.leaseTimeSeconds = leaseTimeSeconds;
    }

    @Override
    public boolean tryLock() {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock == null) {
                return false;
            }
            return lock.tryLock(0, leaseTimeSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取 Redisson 重建锁被中断，lockKey={}", lockKey, e);
            return false;
        } catch (Exception e) {
            log.warn("获取 Redisson 重建锁异常，lockKey={}", lockKey, e);
            return false;
        }
    }

    @Override
    public void unlock() {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("释放 Redisson 重建锁异常，lockKey={}", lockKey, e);
        }
    }
}
