package hk.ljx.fishhub.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import hk.ljx.fishhub.gateway.constant.RedisKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private StpInterfaceImpl stpInterface;

    @BeforeEach
    void setUp() {
        stpInterface = new StpInterfaceImpl();
        ReflectionTestUtils.setField(stpInterface, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(stpInterface, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldReadRolesWrittenByRegistrationAsJsonArray() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildUserRoleKey(100L))).thenReturn("[\"common\"]");

        assertEquals(List.of("common"), stpInterface.getRoleList(100L, "login"));
    }
}
