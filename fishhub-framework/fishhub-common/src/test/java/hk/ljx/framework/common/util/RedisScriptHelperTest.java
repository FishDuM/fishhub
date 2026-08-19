package hk.ljx.framework.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RedisScriptHelperTest {

    @Test
    void shouldLoadLongScriptWithCorrectReturnType() {
        DefaultRedisScript<Long> script = RedisScriptHelper.loadLongScript("/test_script.lua");
        assertNotNull(script);
        assertEquals(Long.class, script.getResultType());
    }

    @Test
    void shouldLoadScriptWithGenericReturnType() {
        DefaultRedisScript<Boolean> script = RedisScriptHelper.loadScript("/test_script.lua", Boolean.class);
        assertNotNull(script);
        assertEquals(Boolean.class, script.getResultType());
    }
}
