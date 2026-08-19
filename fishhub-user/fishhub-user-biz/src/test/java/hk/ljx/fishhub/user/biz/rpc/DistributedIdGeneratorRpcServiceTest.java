package hk.ljx.fishhub.user.biz.rpc;

import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedIdGeneratorRpcServiceTest {

    @Mock
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    @InjectMocks
    private DistributedIdGeneratorRpcService service;

    @Test
    void shouldGenerateFishhubIdThatMatchesProfileRule() {
        when(distributedIdGeneratorFeignApi.getSnowflakeId("leaf-snowflake-fishhub-id"))
                .thenReturn("1892182918291829101");

        assertEquals("fish1892182918291829101", service.getFishhubId());
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        when(distributedIdGeneratorFeignApi.getSnowflakeId("leaf-snowflake-fishhub-id"))
                .thenThrow(new RuntimeException("first call fails"))
                .thenReturn("1892182918291829102");

        assertEquals("fish1892182918291829102", service.getFishhubId());
    }

    @Test
    void shouldFallbackToLocalSnowflakeAfterRetriesExhausted() {
        when(distributedIdGeneratorFeignApi.getSnowflakeId("leaf-snowflake-fishhub-id"))
                .thenThrow(new RuntimeException("always fails"));

        String fishhubId = service.getFishhubId();
        org.junit.jupiter.api.Assertions.assertNotNull(fishhubId);
        org.junit.jupiter.api.Assertions.assertTrue(fishhubId.startsWith("fish"));
    }
}
