package hk.ljx.framework.biz.context.holder;

import com.alibaba.ttl.TtlRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LoginUserContextHolderTest {

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void testBasicSetAndGet() {
        assertNull(LoginUserContextHolder.getUserId());

        LoginUserContextHolder.setUserId(10086L);
        assertEquals(10086L, LoginUserContextHolder.getUserId());

        LoginUserContextHolder.setUserId("10087");
        assertEquals(10087L, LoginUserContextHolder.getUserId());

        LoginUserContextHolder.setUserId((Object) null);
        assertNull(LoginUserContextHolder.getUserId());

        LoginUserContextHolder.setUserId("invalid_id");
        assertNull(LoginUserContextHolder.getUserId());
    }

    @Test
    void testTtlPropagationToThreadPool() throws InterruptedException {
        LoginUserContextHolder.setUserId(99999L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Long> childThreadUserId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 使用 TtlRunnable 装饰任务透传 TTL
        Runnable task = TtlRunnable.get(() -> {
            childThreadUserId.set(LoginUserContextHolder.getUserId());
            latch.countDown();
        });

        assertNotNull(task);
        executor.submit(task);

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(99999L, childThreadUserId.get());

        executor.shutdown();
    }
}
