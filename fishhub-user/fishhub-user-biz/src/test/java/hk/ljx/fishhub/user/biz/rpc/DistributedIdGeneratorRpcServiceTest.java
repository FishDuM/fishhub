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
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-fishhub-id"))
                .thenReturn("10100");

        assertEquals("fish10100", service.getFishhubId());
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-fishhub-id"))
                .thenThrow(new RuntimeException("first call fails"))
                .thenReturn("10101");

        assertEquals("fish10101", service.getFishhubId());
    }

    @Test
    void shouldThrowBizExceptionAfterRetriesExhausted() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-fishhub-id"))
                .thenThrow(new RuntimeException("always fails"));

        assertThrows(hk.ljx.framework.common.exception.BizException.class, () -> service.getFishhubId());
    }
}
