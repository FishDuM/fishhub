package hk.ljx.framework.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SafeRedisUtilTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SafeRedisUtil safeRedisUtil;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        safeRedisUtil = new SafeRedisUtil(redisTemplate);
    }

    @Test
    void testGet_Success() {
        when(valueOperations.get("testKey")).thenReturn("testValue");
        assertEquals("testValue", safeRedisUtil.get("testKey"));
    }

    @Test
    void testGet_RedisDown_ShouldReturnNullWithoutException() {
        when(valueOperations.get("testKey")).thenThrow(new RedisConnectionFailureException("Redis connection refused"));
        assertNull(safeRedisUtil.get("testKey"));
    }

    @Test
    void testSet_Success() {
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        assertTrue(safeRedisUtil.set("k", "v", 10, TimeUnit.SECONDS));
    }

    @Test
    void testSet_RedisDown_ShouldReturnFalseWithoutException() {
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        assertFalse(safeRedisUtil.set("k", "v", 10, TimeUnit.SECONDS));
    }

    @Test
    void testDelete_RedisDown_ShouldReturnFalseWithoutException() {
        when(redisTemplate.delete("k")).thenThrow(new RedisConnectionFailureException("Redis down"));
        assertFalse(safeRedisUtil.delete("k"));
    }

    @Test
    void testGetObject_Success() {
        when(valueOperations.get("userKey")).thenReturn("{\"id\":123,\"name\":\"Alice\"}");
        TestUser user = safeRedisUtil.getObject("userKey", TestUser.class);
        assertNotNull(user);
        assertEquals(123L, user.getId());
        assertEquals("Alice", user.getName());
    }

    @Test
    void testGetObject_RedisDown_ShouldReturnNull() {
        when(valueOperations.get("userKey")).thenThrow(new RedisConnectionFailureException("Redis down"));
        assertNull(safeRedisUtil.getObject("userKey", TestUser.class));
    }

    static class TestUser {
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
