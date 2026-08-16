package hk.ljx.fishhub.auth;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FISHHUB_RUN_INTEGRATION_TESTS", matches = "true")
class RedisTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void valueRoundTrip() {
        String key = "fishhub:test:auth:" + UUID.randomUUID();
        try {
            stringRedisTemplate.opsForValue().set(key, "飞鱼社区");
            assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)));
            assertEquals("飞鱼社区", stringRedisTemplate.opsForValue().get(key));
        } finally {
            stringRedisTemplate.delete(key);
        }
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)));
    }
}
