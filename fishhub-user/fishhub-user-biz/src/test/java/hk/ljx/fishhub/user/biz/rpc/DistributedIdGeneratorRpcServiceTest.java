package hk.ljx.fishhub.user.biz.rpc;

import hk.ljx.framework.id.client.DistributedIdGeneratorClient;
import hk.ljx.framework.id.constant.ApiConstants;
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
    private DistributedIdGeneratorClient distributedIdGeneratorClient;

    @InjectMocks
    private DistributedIdGeneratorRpcService service;

    @Test
    void shouldGenerateFishhubIdThatMatchesProfileRule() {
        when(distributedIdGeneratorClient.getSnowflakeId(ApiConstants.BIZ_TAG_FISHHUB_ID))
                .thenReturn("1892182918291829101");

        assertEquals("fish1892182918291829101", service.getFishhubId());
    }

    @Test
    void shouldGenerateUserId() {
        when(distributedIdGeneratorClient.getSnowflakeId(ApiConstants.BIZ_TAG_USER_ID))
                .thenReturn("1892182918291829102");

        assertEquals("1892182918291829102", service.getUserId());
    }
}
