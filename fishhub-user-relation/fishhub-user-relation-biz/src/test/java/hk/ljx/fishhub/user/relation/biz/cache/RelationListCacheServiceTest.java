package hk.ljx.fishhub.user.relation.biz.cache;

import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

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
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(false, false, true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("following:1", 0L, 10L))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("2", "7")));
        when(followingDOMapper.selectByUserId(1L)).thenReturn(Arrays.asList(
                FollowingDO.builder().userId(1L).followingUserId(2L).createTime(LocalDateTime.now()).build(),
                FollowingDO.builder().userId(1L).followingUserId(7L).createTime(LocalDateTime.now()).build()));

        List<String> members = cacheService.fetchFollowingMembers(1L, 0L, 11);

        assertEquals(Arrays.asList("2", "7"), members);
        // 重建走了单飞锁：获取后必须释放
        verify(stringRedisTemplate).delete("lock:relation:list:rebuild:following:1");
    }

    @Test
    void shouldCreateEmptyZSetAndFallbackToDbWhenEmptyRecords() {
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(false, false, false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(followingDOMapper.selectByUserId(1L)).thenReturn(Collections.emptyList());
        when(followingDOMapper.selectCursorPageByUserId(eq(1L), isNull(), eq(11L))).thenReturn(Collections.emptyList());

        List<String> members = cacheService.fetchFollowingMembers(1L, 0L, 11);

        assertTrue(members.isEmpty());
        // 空列表也占位（锁获取并释放），DB 兜底查询过一次
        verify(stringRedisTemplate).delete("lock:relation:list:rebuild:following:1");
        verify(followingDOMapper).selectCursorPageByUserId(eq(1L), isNull(), eq(11L));
    }

    @Test
    void shouldFallbackToDbWhenRebuildLockIsBusy() {
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(followingDOMapper.selectCursorPageByUserId(eq(1L), isNull(), eq(11L))).thenReturn(Arrays.asList(
                FollowingDO.builder().userId(1L).followingUserId(5L).createTime(LocalDateTime.now()).build()));

        List<String> members = cacheService.fetchFollowingMembers(1L, 0L, 11);

        assertEquals(Collections.singletonList("5"), members);
        // 锁被占用时不重建，也不重复打全量 DB
        verify(followingDOMapper, never()).selectByUserId(anyLong());
        // 未抢到锁则不会释放锁
        verify(stringRedisTemplate, never()).delete("lock:relation:list:rebuild:following:1");
    }

    @Test
    void shouldRebuildFansCacheFromDbWhenKeyMissing() {
        when(stringRedisTemplate.hasKey("fans:9")).thenReturn(false, false, true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("fans:9", 0L, 10L))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("6", "5")));
        when(followingDOMapper.selectCursorPageByFollowingUserId(eq(9L), isNull(), eq(5000L))).thenReturn(Arrays.asList(
                FollowingDO.builder().userId(5L).followingUserId(9L).createTime(LocalDateTime.now()).build(),
                FollowingDO.builder().userId(6L).followingUserId(9L).createTime(LocalDateTime.now()).build()));

        List<String> members = cacheService.fetchFansMembers(9L, 0L, 11);

        assertEquals(Arrays.asList("6", "5"), members);
        // 粉丝列表从 DB 全量重建（最多 5000 条）
        verify(followingDOMapper).selectCursorPageByFollowingUserId(eq(9L), isNull(), eq(5000L));
        verify(stringRedisTemplate).delete("lock:relation:list:rebuild:fans:9");
    }

    @Test
    void shouldWriteNewFanWithTimestampTtlAndFanIdArgs() {
        cacheService.addFan(9L, 2L, LocalDateTime.of(2025, 1, 1, 0, 0));

        // 参数形状：时间戳, 粉丝ID, 过期秒数
        verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    void shouldRemoveFanWithFanIdAndTtlArgs() {
        cacheService.removeFan(9L, 2L);

        // 参数形状：粉丝ID, 过期秒数
        verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), anyList(), any(), any());
    }

    @Test
    void shouldFindFollowedUserIdsFromZSetWhenKeyExists() {
        when(stringRedisTemplate.hasKey("following:1")).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.range("following:1", 0L, -1L))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("1", "2", "9", "5")));

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
