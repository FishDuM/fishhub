package hk.ljx.fishhub.data.align.service;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class DailyChangeDeduplicator {

    private static final long EXPIRE_SECONDS = 20 * 60 * 60;
    private static final RedisScript<Long> EXISTS_SCRIPT = RedisScript.of(
            "return redis.call('SISMEMBER', KEYS[1], ARGV[1])",
            Long.class);
    private static final RedisScript<Long> MARK_SCRIPT = RedisScript.of(
            "redis.call('SADD', KEYS[1], ARGV[1]); "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[2]); return 1",
            Long.class);

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public boolean exists(String key, Long id) {
        Long result = redisTemplate.execute(EXISTS_SCRIPT, Collections.singletonList(key), id);
        return Long.valueOf(1L).equals(result);
    }

    public void markAfterDatabaseSuccess(String key, Long id) {
        redisTemplate.execute(MARK_SCRIPT, Collections.singletonList(key), id, EXPIRE_SECONDS);
    }
}
