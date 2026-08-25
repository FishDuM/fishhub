package hk.ljx.fishhub.user.biz.service.impl;

import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.common.enums.DeletedEnum;
import hk.ljx.framework.common.enums.StatusEnum;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;
import hk.ljx.fishhub.user.biz.domain.mapper.UserDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.UserRoleDOMapper;
import hk.ljx.fishhub.user.biz.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.model.vo.FindUserProfileReqVO;
import hk.ljx.fishhub.user.biz.rpc.DistributedIdGeneratorRpcService;
import hk.ljx.fishhub.user.biz.service.RolePermissionService;
import hk.ljx.fishhub.user.dto.req.FindUserByIdReqDTO;
import hk.ljx.fishhub.user.dto.req.FindUsersByIdsReqDTO;
import hk.ljx.fishhub.user.dto.req.FindUserByPhoneReqDTO;
import hk.ljx.fishhub.user.dto.req.ResolveLoginableUserReqDTO;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.dto.rsp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.rsp.ResolveLoginableUserRspDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserDOMapper userDOMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    @Mock
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Mock
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Mock
    private UserRoleDOMapper userRoleDOMapper;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void findByIdsShouldMergeRedisHitsAndDatabaseMisses() {
        FindUserByIdRspDTO cachedUser = FindUserByIdRspDTO.builder()
                .id(2L)
                .nickName("fish2")
                .build();
        UserDO databaseUser = UserDO.builder()
                .id(3L)
                .nickname("fish3")
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.multiGet(List.of("user:info:2", "user:info:3")))
                .thenReturn(Arrays.asList(JsonUtils.toJsonString(cachedUser), null));
        when(userDOMapper.selectByIds(List.of(3L))).thenReturn(List.of(databaseUser));

        FindUsersByIdsReqDTO request = new FindUsersByIdsReqDTO();
        request.setIds(List.of(2L, 3L));

        Response<List<FindUserByIdRspDTO>> response = userService.findByIds(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(List.of(2L, 3L), response.getData().stream()
                .map(FindUserByIdRspDTO::getId)
                .toList());
        verify(userDOMapper).selectByIds(List.of(3L));
    }

    @Test
    void findActiveByIdShouldReturnCachedUserWithoutQueryingDb() {
        FindUserByIdRspDTO cachedUser = FindUserByIdRspDTO.builder()
                .id(2L)
                .nickName("fish2")
                .build();
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get("user:info:2")).thenReturn(JsonUtils.toJsonString(cachedUser));

        FindUserByIdReqDTO request = new FindUserByIdReqDTO();
        request.setId(2L);

        Response<FindUserByIdRspDTO> response = userService.findActiveById(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(2L, response.getData().getId());
        verify(userDOMapper, never()).selectActiveById(anyLong());
    }

    @Test
    void findActiveByIdShouldQueryActiveUserOnCacheMiss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get("user:info:1")).thenReturn(null);
        when(userDOMapper.selectActiveById(1L)).thenReturn(null);

        FindUserByIdReqDTO request = new FindUserByIdReqDTO();
        request.setId(1L);

        Response<FindUserByIdRspDTO> response = userService.findActiveById(request);

        assertTrue(response.isSuccess());
        assertNull(response.getData());
        verify(stringValueOperations).set("user:info:1", "null", 3L, TimeUnit.SECONDS);
    }

    @Test
    void findByPhoneShouldOnlyReturnActiveUser() {
        UserDO user = UserDO.builder().id(1L).password("encoded-password").build();
        FindUserByPhoneReqDTO request = new FindUserByPhoneReqDTO();
        request.setPhone("13800138000");
        when(userDOMapper.selectActiveByPhone("13800138000")).thenReturn(user);

        Response<FindUserByPhoneRspDTO> response = userService.findByPhone(request);

        assertTrue(response.isSuccess());
        assertEquals(1L, response.getData().getId());
        assertEquals("encoded-password", response.getData().getPassword());
        verify(userDOMapper).selectActiveByPhone("13800138000");
    }

    @Test
    void resolveOrRegisterShouldNotReturnDisabledAccount() {
        ResolveLoginableUserReqDTO request = ResolveLoginableUserReqDTO.builder()
                .phone("13800138000")
                .build();
        when(userDOMapper.selectByPhone("13800138000"))
                .thenReturn(UserDO.builder()
                        .id(1L)
                        .status(StatusEnum.DISABLED.getValue())
                        .isDeleted(DeletedEnum.NO.getValue())
                        .build());

        Response<ResolveLoginableUserRspDTO> response = userService.resolveOrRegisterLoginableUser(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertFalse(response.getData().isLoginable());
        assertNull(response.getData().getUserId());
        verify(userDOMapper).selectByPhone("13800138000");
    }

    @Test
    void resolveOrRegisterShouldUseWinningAccountWhenConcurrentInsertWins() {
        ResolveLoginableUserReqDTO request = ResolveLoginableUserReqDTO.builder()
                .phone("13800138000")
                .build();
        when(userDOMapper.selectByPhone("13800138000"))
                .thenReturn(null, UserDO.builder()
                        .id(2L)
                        .status(StatusEnum.DISABLED.getValue())
                        .isDeleted(DeletedEnum.NO.getValue())
                        .build());
        when(distributedIdGeneratorRpcService.getFishhubId()).thenReturn("fish100");
        when(distributedIdGeneratorRpcService.getUserId()).thenReturn("100");
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(userDOMapper.insertIfAbsent(any())).thenReturn(0);

        Response<ResolveLoginableUserRspDTO> response = userService.resolveOrRegisterLoginableUser(request);

        assertFalse(response.getData().isLoginable());
        assertNull(response.getData().getUserId());
        verify(userDOMapper, times(2)).selectByPhone("13800138000");
    }

    @Test
    void resolveOrRegisterShouldGrantDefaultRole() {
        ResolveLoginableUserReqDTO request = ResolveLoginableUserReqDTO.builder()
                .phone("13800138000")
                .build();
        when(userDOMapper.selectByPhone("13800138000")).thenReturn(null);
        when(distributedIdGeneratorRpcService.getFishhubId()).thenReturn("fish100");
        when(distributedIdGeneratorRpcService.getUserId()).thenReturn("100");
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(userDOMapper.insertIfAbsent(any())).thenReturn(1);

        Response<ResolveLoginableUserRspDTO> response = userService.resolveOrRegisterLoginableUser(request);

        assertTrue(response.getData().isLoginable());
        verify(userRoleDOMapper).insert(any());
    }

    @Test
    void findUserProfileShouldThrowWhenUserIdIsNull() {
        FindUserProfileReqVO request = new FindUserProfileReqVO();
        request.setUserId(null);

        BizException exception = assertThrows(BizException.class, () -> userService.findUserProfile(request));
        assertEquals(ResponseCodeEnum.USER_NOT_FOUND.getErrorCode(), exception.getErrorCode());
        verify(stringRedisTemplate, never()).opsForValue();
        verify(userDOMapper, never()).selectByPrimaryKey(any());
    }
}
