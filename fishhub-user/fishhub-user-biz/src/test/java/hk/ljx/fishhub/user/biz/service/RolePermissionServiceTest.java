package hk.ljx.fishhub.user.biz.service;

import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;
import hk.ljx.fishhub.user.biz.domain.mapper.UserDOMapper;
import hk.ljx.fishhub.user.dto.rsp.UserRolePermissionRspDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private UserDOMapper userDOMapper;

    @InjectMocks
    private RolePermissionService service;

    @Test
    void shouldReturnDefaultRolesAndPermissionsForActiveUser() {
        when(userDOMapper.selectByPrimaryKey(1L)).thenReturn(
                UserDO.builder().id(1L).status(0).isDeleted(false).build());

        UserRolePermissionRspDTO result = service.findByUserId(1L);

        assertEquals(List.of("common_user"), result.getRoles());
        assertEquals(List.of("app:note:publish", "app:comment:publish"), result.getPermissions());
    }

    @Test
    void shouldReturnEmptyWhenUserNotFoundOrDisabled() {
        when(userDOMapper.selectByPrimaryKey(2L)).thenReturn(null);
        UserRolePermissionRspDTO resultNull = service.findByUserId(2L);
        assertEquals(List.of(), resultNull.getRoles());
        assertEquals(List.of(), resultNull.getPermissions());

        when(userDOMapper.selectByPrimaryKey(3L)).thenReturn(
                UserDO.builder().id(3L).status(1).isDeleted(false).build());
        UserRolePermissionRspDTO resultDisabled = service.findByUserId(3L);
        assertEquals(List.of(), resultDisabled.getRoles());
        assertEquals(List.of(), resultDisabled.getPermissions());

        when(userDOMapper.selectByPrimaryKey(4L)).thenReturn(
                UserDO.builder().id(4L).status(0).isDeleted(true).build());
        UserRolePermissionRspDTO resultDeleted = service.findByUserId(4L);
        assertEquals(List.of(), resultDeleted.getRoles());
        assertEquals(List.of(), resultDeleted.getPermissions());
    }

    @Test
    void shouldHandleNullUserIdGracefully() {
        UserRolePermissionRspDTO result = service.findByUserId(null);
        assertEquals(List.of(), result.getRoles());
        assertEquals(List.of(), result.getPermissions());
    }
}

