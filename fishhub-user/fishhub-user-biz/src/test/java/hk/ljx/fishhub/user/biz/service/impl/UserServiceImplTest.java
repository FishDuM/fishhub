package hk.ljx.fishhub.user.biz.service.impl;

import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;
import hk.ljx.fishhub.user.biz.domain.mapper.UserDOMapper;
import hk.ljx.fishhub.user.dto.req.FindUsersByIdsReqDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserDOMapper userDOMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

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

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of("user:info:2", "user:info:3")))
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
}
