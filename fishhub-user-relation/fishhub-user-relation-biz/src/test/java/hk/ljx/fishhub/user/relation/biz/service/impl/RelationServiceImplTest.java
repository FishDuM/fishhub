package hk.ljx.fishhub.user.relation.biz.service.impl;

import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.relation.biz.cache.RelationListCacheService;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.model.vo.FindFansListReqVO;
import hk.ljx.fishhub.user.relation.biz.model.vo.FindFansUserRspVO;
import hk.ljx.fishhub.user.relation.biz.model.vo.FindFollowingListReqVO;
import hk.ljx.fishhub.user.relation.biz.model.vo.FindFollowingUserRspVO;
import hk.ljx.fishhub.user.relation.biz.model.vo.FollowUserReqVO;
import hk.ljx.fishhub.user.relation.biz.model.vo.RelationCursorPageResponse;
import hk.ljx.fishhub.user.relation.biz.model.vo.UnfollowUserReqVO;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.user.client.UserClient;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationServiceImplTest {

    @Mock
    private RelationListCacheService relationListCacheService;
    @Mock
    private UserClient userClient;
    @Mock
    private CountClient countClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private FollowingDOMapper followingDOMapper;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @InjectMocks
    private RelationServiceImpl relationService;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void shouldReturnFirstPageOfFollowingListWithNextCursor() {
        when(relationListCacheService.fetchFollowingMembers(1L, 0L, 11))
                .thenReturn(Arrays.asList("2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"));
        when(userClient.findByIds(anyList())).thenReturn(users(2L, 12L));

        RelationCursorPageResponse<FindFollowingUserRspVO> resp =
                relationService.findFollowingList(FindFollowingListReqVO.builder().userId(1L).cursor(0L).build());

        assertEquals(10, resp.getData().size());
        assertEquals(10L, resp.getNextCursor());
        assertEquals(2L, resp.getData().get(0).getUserId());
        assertTrue(resp.getData().get(0).getIsFollowed());
    }

    @Test
    void shouldReturnTailPageWithoutNextCursor() {
        when(relationListCacheService.fetchFollowingMembers(1L, 20L, 11))
                .thenReturn(Arrays.asList("30", "31", "32"));
        when(userClient.findByIds(anyList())).thenReturn(users(30L, 33L));

        RelationCursorPageResponse<FindFollowingUserRspVO> resp =
                relationService.findFollowingList(FindFollowingListReqVO.builder().userId(1L).cursor(20L).build());

        assertEquals(3, resp.getData().size());
        assertNull(resp.getNextCursor());
    }

    @Test
    void shouldReturnEmptyWhenFollowingListHasNoMoreMember() {
        when(relationListCacheService.fetchFollowingMembers(1L, 0L, 11)).thenReturn(Collections.emptyList());

        RelationCursorPageResponse<FindFollowingUserRspVO> resp =
                relationService.findFollowingList(FindFollowingListReqVO.builder().userId(1L).cursor(0L).build());

        assertTrue(resp.getData().isEmpty());
        assertNull(resp.getNextCursor());
        verify(userClient, never()).findByIds(anyList());
    }

    @Test
    void shouldReturnFansWithCountsAndFollowedFlags() {
        when(relationListCacheService.fetchFansMembers(9L, 0L, 11))
                .thenReturn(Arrays.asList("2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"));
        when(userClient.findByIds(anyList())).thenReturn(users(2L, 12L));
        when(countClient.findByUserIds(anyList())).thenReturn(counts(2L, 12L));
        when(relationListCacheService.findFollowedUserIds(isNull(), anyList())).thenReturn(Collections.singleton(2L));

        RelationCursorPageResponse<FindFansUserRspVO> resp =
                relationService.findFansList(FindFansListReqVO.builder().userId(9L).cursor(0L).build());

        assertEquals(10, resp.getData().size());
        assertEquals(10L, resp.getNextCursor());
        FindFansUserRspVO first = resp.getData().get(0);
        assertEquals(2L, first.getUserId());
        assertTrue(first.getIsFollowed());
        assertEquals(20L, first.getNoteTotal());
        assertEquals(30L, first.getFansTotal());
        assertFalse(resp.getData().get(1).getIsFollowed());
    }

    @Test
    void shouldReturnEmptyWhenFansListEmpty() {
        when(relationListCacheService.fetchFansMembers(9L, 0L, 11)).thenReturn(Collections.emptyList());

        RelationCursorPageResponse<FindFansUserRspVO> resp =
                relationService.findFansList(FindFansListReqVO.builder().userId(9L).cursor(0L).build());

        assertTrue(resp.getData().isEmpty());
        assertNull(resp.getNextCursor());
        verify(userClient, never()).findByIds(anyList());
    }

    @Test
    void followShouldRejectDisabledOrDeletedUser() {
        LoginUserContextHolder.setUserId(1L);
        when(userClient.findActiveById(2L)).thenReturn(null);

        assertThrows(BizException.class, () -> relationService.follow(
                FollowUserReqVO.builder().followUserId(2L).build()));

        verify(stringRedisTemplate, never()).execute(
                any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void unfollowShouldSucceedEvenWhenTargetUserIsDisabledOrDeleted() {
        LoginUserContextHolder.setUserId(1L);
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        Response<?> response = relationService.unfollow(
                UnfollowUserReqVO.builder().unfollowUserId(2L).build());

        assertTrue(response.isSuccess());
        verify(userClient, never()).findById(anyLong());
        verify(userClient, never()).findActiveById(anyLong());
    }

    private List<FindUserByIdRspDTO> users(long from, long toExclusive) {
        List<FindUserByIdRspDTO> list = new ArrayList<>();
        for (long id = from; id < toExclusive; id++) {
            list.add(FindUserByIdRspDTO.builder()
                    .id(id)
                    .nickName("u" + id)
                    .avatar("a" + id)
                    .introduction("i" + id)
                    .build());
        }
        return list;
    }

    private List<FindUserCountsByIdRspDTO> counts(long from, long toExclusive) {
        List<FindUserCountsByIdRspDTO> list = new ArrayList<>();
        for (long id = from; id < toExclusive; id++) {
            list.add(FindUserCountsByIdRspDTO.builder()
                    .userId(id)
                    .noteTotal(id * 10L)
                    .fansTotal(id * 15L)
                    .build());
        }
        return list;
    }
}
