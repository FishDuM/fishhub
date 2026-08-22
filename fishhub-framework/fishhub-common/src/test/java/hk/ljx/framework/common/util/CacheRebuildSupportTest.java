package hk.ljx.framework.common.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheRebuildSupportTest {

    private static class TestLock implements RebuildLock {
        final AtomicInteger lockCount = new AtomicInteger();
        final AtomicInteger unlockCount = new AtomicInteger();
        private final boolean acquired;

        TestLock(boolean acquired) {
            this.acquired = acquired;
        }

        @Override
        public boolean tryLock() {
            lockCount.incrementAndGet();
            return acquired;
        }

        @Override
        public void unlock() {
            unlockCount.incrementAndGet();
        }
    }

    private static RebuildLock brokenLock() {
        return new RebuildLock() {
            @Override
            public boolean tryLock() {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void unlock() {
            }
        };
    }

    @Test
    void lockLostUsesFallback() {
        TestLock lock = new TestLock(false);
        String result = CacheRebuildSupport.getOrRebuild(lock, 2, 1, () -> null, () -> "rebuilt", () -> "fallback");
        assertEquals("fallback", result);
        assertEquals(1, lock.lockCount.get());
        assertEquals(0, lock.unlockCount.get());
    }

    @Test
    void lockLostCanSeeValueWrittenByWinner() {
        AtomicInteger reads = new AtomicInteger();
        String result = CacheRebuildSupport.getOrRebuild(new TestLock(false), 3, 1,
                () -> reads.incrementAndGet() >= 2 ? "written" : null, () -> "rebuilt", () -> "fallback");
        assertEquals("written", result);
    }

    @Test
    void lockWonRebuildsOnMiss() {
        TestLock lock = new TestLock(true);
        AtomicInteger rebuilds = new AtomicInteger();
        String result = CacheRebuildSupport.getOrRebuild(lock, 1, 1, () -> null,
                () -> {
                    rebuilds.incrementAndGet();
                    return "rebuilt";
                }, () -> "fallback");
        assertEquals("rebuilt", result);
        assertEquals(1, rebuilds.get());
        assertEquals(1, lock.lockCount.get());
        assertEquals(1, lock.unlockCount.get());
    }

    @Test
    void lockWonSkipsRebuildWhenDoubleCheckHits() {
        TestLock lock = new TestLock(true);
        AtomicInteger rebuilds = new AtomicInteger();
        String result = CacheRebuildSupport.getOrRebuild(lock, 1, 1, () -> "cached",
                () -> {
                    rebuilds.incrementAndGet();
                    return "rebuilt";
                }, () -> "fallback");
        assertEquals("cached", result);
        assertEquals(0, rebuilds.get());
        assertEquals(1, lock.unlockCount.get());
    }

    @Test
    void lockFailureFallsBack() {
        AtomicInteger unlockCount = new AtomicInteger();
        RebuildLock lock = new RebuildLock() {
            @Override
            public boolean tryLock() {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void unlock() {
                unlockCount.incrementAndGet();
            }
        };
        String result = CacheRebuildSupport.getOrRebuild(lock, 1, 1, () -> null, () -> "rebuilt", () -> "fallback");
        assertEquals("fallback", result);
        assertEquals(0, unlockCount.get());
    }

    @Test
    void rebuildFailurePropagatesAndUnlocks() {
        AtomicInteger unlockCount = new AtomicInteger();
        RebuildLock lock = new RebuildLock() {
            @Override
            public boolean tryLock() {
                return true;
            }

            @Override
            public void unlock() {
                unlockCount.incrementAndGet();
            }
        };
        assertThrows(IllegalStateException.class, () ->
                CacheRebuildSupport.getOrRebuild(lock, 1, 1, () -> null,
                        () -> {
                            throw new IllegalStateException("db down");
                        }, () -> "fallback"));
        assertEquals(1, unlockCount.get());
    }

    @Test
    void skipsWhenCachePresent() {
        TestLock lock = new TestLock(true);
        AtomicInteger rebuilds = new AtomicInteger();
        CacheRebuildSupport.rebuildIfMissing(lock, 1, 1, () -> true, rebuilds::incrementAndGet);
        assertEquals(0, rebuilds.get());
        assertEquals(1, lock.lockCount.get());
        assertEquals(1, lock.unlockCount.get());
    }

    @Test
    void rebuildsWhenLockWon() {
        TestLock lock = new TestLock(true);
        AtomicInteger rebuilds = new AtomicInteger();
        CacheRebuildSupport.rebuildIfMissing(lock, 1, 1, () -> false, rebuilds::incrementAndGet);
        assertEquals(1, rebuilds.get());
        assertEquals(1, lock.unlockCount.get());
    }

    @Test
    void lockLostWaitsForWinner() {
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger rebuilds = new AtomicInteger();
        CacheRebuildSupport.rebuildIfMissing(new TestLock(false), 2, 1,
                () -> checks.incrementAndGet() > 1, rebuilds::incrementAndGet);
        assertEquals(0, rebuilds.get());
    }

    @Test
    void lockFailureIsSilent() {
        AtomicInteger rebuilds = new AtomicInteger();
        CacheRebuildSupport.rebuildIfMissing(brokenLock(), 1, 1, () -> false, rebuilds::incrementAndGet);
        assertEquals(0, rebuilds.get());
    }
}
