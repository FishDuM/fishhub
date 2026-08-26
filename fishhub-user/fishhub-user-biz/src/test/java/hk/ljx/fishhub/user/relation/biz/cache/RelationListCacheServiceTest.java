package hk.ljx.fishhub.user.relation.biz.cache;

import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationListCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private FollowingDOMapper followingDOMapper;
    @InjectMocks
    private RelationListCacheService cacheService;

    @Test
    void shouldReadFollowingMembersFromCacheWhenKeyExists() {
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("following:1", 0L, 10L))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("3", "2", "1")));

        List<String> members = cacheService.fetchFollowingMembers(1L, 0L, 11);

        assertEquals(Arrays.asList("3", "2", "1"), members);
        verify(followingDOMapper, never()).selectByUserId(anyLong());
    }

    @Test
    void shouldRebuildFollowingCacheAndReadWhenKeyMissing() {
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(false);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("following:1", 0L, 10L))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("2", "7")));
        when(followingDOMapper.selectByUserId(1L)).thenReturn(Arrays.asList(
                FollowingDO.builder().userId(1L).followingUserId(2L).createTime(LocalDateTime.now()).build(),
                FollowingDO.builder().userId(1L).followingUserId(7L).createTime(LocalDateTime.now()).build()));

        List<String> members = cacheService.fetchFollowingMembers(1L, 0L, 11);

        assertEquals(Arrays.asList("2", "7"), members);
        verify(followingDOMapper).selectByUserId(1L);
    }

    @Test
    void shouldCreateEmptyZSetWhenEmptyRecords() {
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(false);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(followingDOMapper.selectByUserId(1L)).thenReturn(Collections.emptyList());

        List<String> members = cacheService.fetchFollowingMembers(1L, 0L, 11);

        assertTrue(members.isEmpty());
        verify(followingDOMapper).selectByUserId(1L);
        verify(followingDOMapper, never()).selectCursorPageByUserId(anyLong(), any(), anyLong());
    }

    @Test
    void shouldRebuildFansCacheFromDbWhenKeyMissing() {
        when(stringRedisTemplate.hasKey("fans:9")).thenReturn(false);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("fans:9", 0L, 10L))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("6", "5")));
        when(followingDOMapper.selectCursorPageByFollowingUserId(eq(9L), isNull(), eq(5000L))).thenReturn(Arrays.asList(
                FollowingDO.builder().userId(5L).followingUserId(9L).createTime(LocalDateTime.now()).build(),
                FollowingDO.builder().userId(6L).followingUserId(9L).createTime(LocalDateTime.now()).build()));

        List<String> members = cacheService.fetchFansMembers(9L, 0L, 11);

        assertEquals(Arrays.asList("6", "5"), members);
        verify(followingDOMapper).selectCursorPageByFollowingUserId(eq(9L), isNull(), eq(5000L));
    }

    @Test
    void shouldWriteNewFanWhenKeyExists() {
        when(stringRedisTemplate.hasKey("fans:9")).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard("fans:9")).thenReturn(10L);

        cacheService.addFan(9L, 2L, LocalDateTime.of(2025, 1, 1, 0, 0));

        verify(zSetOperations).add(eq("fans:9"), eq("2"), anyDouble());
        verify(stringRedisTemplate).expire(eq("fans:9"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void shouldRemoveFanWhenKeyExists() {
        when(stringRedisTemplate.hasKey("fans:9")).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        cacheService.removeFan(9L, 2L);

        verify(zSetOperations).remove(eq("fans:9"), eq("2"));
        verify(stringRedisTemplate).expire(eq("fans:9"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void shouldFindFollowedUserIdsFromZSetWhenKeyExists() {
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(true);
        when(stringRedisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(Arrays.asList(100.0, 200.0, null));

        Set<Long> followed = cacheService.findFollowedUserIds(1L, Arrays.asList(2L, 9L, 7L));

        assertEquals(new HashSet<>(Arrays.asList(2L, 9L)), followed);
        verify(followingDOMapper, never()).selectFollowingUserIds(anyLong(), anyList());
    }

    @Test
    void shouldFindFollowedUserIdsFromDbWhenZSetMissing() {
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(false);
        when(followingDOMapper.selectFollowingUserIds(eq(1L), anyList())).thenReturn(Arrays.asList(2L, 9L));

        Set<Long> followed = cacheService.findFollowedUserIds(1L, Arrays.asList(2L, 9L, 7L));

        assertEquals(new HashSet<>(Arrays.asList(2L, 9L)), followed);
    }
}
