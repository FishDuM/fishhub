package hk.ljx.fishhub.user.biz.service;

import hk.ljx.fishhub.user.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.biz.domain.dataobject.PermissionDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.RoleDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.RolePermissionDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserRoleDO;
import hk.ljx.fishhub.user.biz.domain.mapper.PermissionDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.RoleDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.RolePermissionDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.UserRoleDOMapper;
import hk.ljx.fishhub.user.dto.rsp.UserRolePermissionRspDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private UserRoleDOMapper userRoleDOMapper;
    @Mock
    private RoleDOMapper roleDOMapper;
    @Mock
    private RolePermissionDOMapper rolePermissionDOMapper;
    @Mock
    private PermissionDOMapper permissionDOMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RolePermissionService service;

    @Test
    void shouldServeFromCacheWithoutTouchingDb() {
        String key = RedisKeyConstants.buildUserRolePermissionKey(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(
                "{\"userId\":1,\"roles\":[\"common\"],\"permissions\":[\"note:publish\"]}");

        UserRolePermissionRspDTO result = service.findByUserId(1L);

        assertEquals(List.of("common"), result.getRoles());
        assertEquals(List.of("note:publish"), result.getPermissions());
        verify(userRoleDOMapper, never()).selectEnabledByUserId(anyLong());
        verify(roleDOMapper, never()).selectByPrimaryKey(anyLong());
    }

    @Test
    void shouldLoadFromDbAndWriteCacheOnMiss() {
        String key = RedisKeyConstants.buildUserRolePermissionKey(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);
        when(userRoleDOMapper.selectEnabledByUserId(1L)).thenReturn(List.of(
                UserRoleDO.builder().userId(1L).roleId(2L).build()));
        when(roleDOMapper.selectByPrimaryKey(2L)).thenReturn(
                RoleDO.builder().id(2L).roleKey("common").status(0).isDeleted(false).build());
        when(rolePermissionDOMapper.selectByRoleIds(List.of(2L))).thenReturn(List.of(
                RolePermissionDO.builder().roleId(2L).permissionId(3L).build()));
        when(permissionDOMapper.selectAppEnabledList()).thenReturn(List.of(
                PermissionDO.builder().id(3L).permissionKey("note:publish").build()));

        UserRolePermissionRspDTO result = service.findByUserId(1L);

        assertEquals(List.of("common"), result.getRoles());
        assertEquals(List.of("note:publish"), result.getPermissions());
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(key), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldFallBackToDbWhenRedisIsUnavailable() {
        String key = RedisKeyConstants.buildUserRolePermissionKey(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenThrow(new IllegalStateException("redis unavailable"));
        when(userRoleDOMapper.selectEnabledByUserId(1L)).thenReturn(List.of());

        UserRolePermissionRspDTO result = service.findByUserId(1L);

        assertEquals(List.of(), result.getRoles());
        verify(userRoleDOMapper).selectEnabledByUserId(1L);
    }

    @Test
    void shouldEvictCacheOnRoleChange() {
        service.evict(1L);

        verify(stringRedisTemplate).delete(RedisKeyConstants.buildUserRolePermissionKey(1L));
    }
}
