package hk.ljx.fishhub.user.biz.rpc;

import cn.hutool.core.lang.Snowflake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedIdGeneratorRpcServiceTest {

    @Mock
    private Snowflake snowflake;

    @InjectMocks
    private DistributedIdGeneratorRpcService service;

    @Test
    void shouldGenerateFishhubIdThatMatchesProfileRule() {
        when(snowflake.nextId()).thenReturn(1892182918291829101L);

        assertEquals("fish1892182918291829101", service.getFishhubId());
    }

    @Test
    void shouldGenerateUserId() {
        when(snowflake.nextId()).thenReturn(1892182918291829102L);

        assertEquals("1892182918291829102", service.getUserId());
    }
}
