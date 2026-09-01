package hk.ljx.fishhub.comment.biz.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RedisKeyConstantsTest {

    @Test
    void shouldUseDedicatedKeyForOneLevelCommentPageTotal() {
        String key = RedisKeyConstants.buildOneLevelCommentTotalCacheKey(100L);
        String lockKey = RedisKeyConstants.buildOneLevelCommentTotalCacheLockKey(100L);

        assertEquals("cache:comment:one-level-total:100", key);
        assertEquals("lock:comment:one-level-total:100", lockKey);
        assertNotEquals("count:note:100", key);
        assertNotEquals(key, lockKey);
    }
}
