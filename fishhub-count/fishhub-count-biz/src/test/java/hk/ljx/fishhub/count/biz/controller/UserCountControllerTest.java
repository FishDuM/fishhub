package hk.ljx.fishhub.count.biz.controller;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.biz.service.UserCountService;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCountControllerTest {

    @Mock
    private UserCountService userCountService;

    @InjectMocks
    private UserCountController controller;

    @Test
    void shouldDelegateUserCountQueryWithoutArtificialFailure() {
        FindUserCountsByIdReqDTO request = FindUserCountsByIdReqDTO.builder().userId(27L).build();
        Response<FindUserCountsByIdRspDTO> expected = Response.success(
                FindUserCountsByIdRspDTO.builder().userId(27L).fansTotal(12L).build());
        when(userCountService.findUserCountData(request)).thenReturn(expected);

        Response<FindUserCountsByIdRspDTO> actual = controller.findUserCountData(request);

        assertSame(expected, actual);
        verify(userCountService).findUserCountData(request);
    }
}
