package hk.ljx.framework.common.util;

/**
 * 缓存单飞重建锁：由调用方基于 Redisson RLock 或 Redis SETNX 等机制实现。
 * <p>tryLock 返回 false 表示未获取到锁（调用方将转入轮询等待）；抛出异常表示锁基础设施不可用。</p>
 */
public interface RebuildLock {

    boolean tryLock();

    void unlock();
}
