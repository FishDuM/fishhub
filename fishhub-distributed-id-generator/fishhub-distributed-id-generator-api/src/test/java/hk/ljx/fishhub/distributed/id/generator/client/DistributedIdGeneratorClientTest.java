package hk.ljx.fishhub.distributed.id.generator.client;

import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedIdGeneratorClientTest {

    @Mock
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    @InjectMocks
    private DistributedIdGeneratorClient client;

    @Test
    void shouldGetSnowflakeIdSuccessfully() {
        when(distributedIdGeneratorFeignApi.getSnowflakeId("leaf-snowflake-test"))
                .thenReturn("1892182918291829101");

        String id = client.getSnowflakeId("leaf-snowflake-test");
        assertEquals("1892182918291829101", id);
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        when(distributedIdGeneratorFeignApi.getSnowflakeId("leaf-snowflake-test"))
                .thenThrow(new RuntimeException("first attempt fails"))
                .thenReturn("1892182918291829102");

        String id = client.getSnowflakeId("leaf-snowflake-test");
        assertEquals("1892182918291829102", id);
        verify(distributedIdGeneratorFeignApi, times(2)).getSnowflakeId("leaf-snowflake-test");
    }

    @Test
    void shouldFallbackToLocalSnowflakeWhenAllRetriesFail() {
        when(distributedIdGeneratorFeignApi.getSnowflakeId("leaf-snowflake-test"))
                .thenThrow(new RuntimeException("always fails"));

        String id = client.getSnowflakeId("leaf-snowflake-test");
        assertNotNull(id);
        assertTrue(id.length() >= 18);
        verify(distributedIdGeneratorFeignApi, times(2)).getSnowflakeId("leaf-snowflake-test");
    }

    @Test
    void shouldGetSegmentIdSuccessfully() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-test"))
                .thenReturn("1001");

        String id = client.getSegmentId("leaf-segment-test");
        assertEquals("1001", id);
    }

    @Test
    void shouldFallbackToLocalSnowflakeWhenSegmentAllRetriesFail() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-test"))
                .thenThrow(new RuntimeException("segment fails"));

        String id = client.getSegmentId("leaf-segment-test");
        assertNotNull(id);
        assertTrue(id.length() >= 18);
        verify(distributedIdGeneratorFeignApi, times(2)).getSegmentId("leaf-segment-test");
    }
}
