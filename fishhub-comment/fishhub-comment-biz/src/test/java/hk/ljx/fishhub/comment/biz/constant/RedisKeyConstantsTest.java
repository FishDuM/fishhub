package hk.ljx.fishhub.comment.biz.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RedisKeyConstantsTest {

    @Test
    void shouldUseDedicatedKeyForOneLevelCommentPageTotal() {
        String key = RedisKeyConstants.buildOneLevelCommentTotalCacheKey(100L, "7");
        String versionKey = RedisKeyConstants.buildOneLevelCommentTotalCacheVersionKey(100L);
        String lockKey = RedisKeyConstants.buildOneLevelCommentTotalCacheLockKey(100L);

        assertEquals("cache:comment:one-level-total:100:v:7", key);
        assertEquals("version:comment:one-level-total:100", versionKey);
        assertEquals("lock:comment:one-level-total:100", lockKey);
        assertNotEquals("count:note:100", key);
        assertNotEquals(key, lockKey);
    }
}
