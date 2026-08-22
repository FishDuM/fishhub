package hk.ljx.fishhub.distributed.id.generator.client;

import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldFailFastWhenAllRetriesFail() {
        when(distributedIdGeneratorFeignApi.getSnowflakeId("leaf-snowflake-test"))
                .thenThrow(new RuntimeException("always fails"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> client.getSnowflakeId("leaf-snowflake-test"));
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
    void shouldFailFastWhenSegmentAllRetriesFail() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-test"))
                .thenThrow(new RuntimeException("segment fails"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> client.getSegmentId("leaf-segment-test"));
        verify(distributedIdGeneratorFeignApi, times(2)).getSegmentId("leaf-segment-test");
    }
}
