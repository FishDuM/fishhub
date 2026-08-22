package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.bo.CommentFirstReplyBO;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 评论变更本地动作（列表缓存/热度/首条回复）合并后的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CommentChangedLocalHandlerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private CommentDetailCache commentDetailCache;
    @Mock
    private CommentDOMapper commentDOMapper;
    @Mock
    private CommentHeatAggregator commentHeatAggregator;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @InjectMocks
    private CommentChangedLocalHandler handler;

    // —— 缓存维护 ——

    @Test
    void shouldAddOneLevelCommentAndChildCommentOnPublish() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(zSetOperations.zCard(anyString())).thenReturn(1L);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(Collections.emptyList());

        handler.handlePublish(event(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH,
                List.of(item(1L, 1, 10L), item(2L, 2, 1L))));

        String listKey = RedisKeyConstants.buildCommentListKey(10L);
        verify(zSetOperations).add(listKey, "1", 0D);
        verify(zSetOperations).add(eq(RedisKeyConstants.buildChildCommentListKey(1L)), eq("2"), anyDouble());
        verify(stringRedisTemplate).expire(listKey, 5 * 3600L, TimeUnit.SECONDS);
        verify(zSetOperations, never()).removeRange(anyString(), anyLong(), anyLong());
    }

    @Test
    void shouldTrimOneLevelListWhenOverCap() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(zSetOperations.zCard(anyString())).thenReturn(501L);

        handler.handlePublish(event(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH, List.of(item(1L, 1, 10L))));

        verify(zSetOperations).removeRange(RedisKeyConstants.buildCommentListKey(10L), 0L, -501L);
    }

    @Test
    void shouldRemoveOneLevelCommentOnDeleteAndInvalidateDetailCache() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        handler.handleDelete(event(MQConstants.COMMENT_CHANGE_TYPE_DELETE,
                List.of(item(1L, 1, 10L), item(2L, 2, 1L))));

        verify(zSetOperations).remove(RedisKeyConstants.buildCommentListKey(10L), "1");
        verify(zSetOperations).remove(RedisKeyConstants.buildChildCommentListKey(1L), "2");
        verify(stringRedisTemplate).delete(RedisKeyConstants.buildHaveFirstReplyCommentKey(1L));
        verify(commentDetailCache).delete(any());
        verifyNoMoreInteractions(commentDetailCache);
    }

    @Test
    void shouldRejectEventWithoutItems() {
        CommentChangedEventMqDTO emptyEvent = CommentChangedEventMqDTO.builder()
                .changeType(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH)
                .items(Collections.emptyList())
                .build();
        assertThrows(IllegalArgumentException.class, () -> handler.handlePublish(emptyEvent));
    }

    // —— 首条回复回填 ——

    @Test
    void shouldBatchFillFirstReplyForPendingComments() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(List.of(
                CommentDO.builder().id(101L).firstReplyCommentId(0L).build()));
        when(commentDOMapper.selectEarliestFirstReplyByParentIds(List.of(101L))).thenReturn(List.of(
                CommentDO.builder().id(1001L).parentId(101L).build()));

        handler.handlePublish(publishEventWithTwoLevel(101L));

        ArgumentCaptor<List<CommentFirstReplyBO>> captor = ArgumentCaptor.forClass(List.class);
        verify(commentDOMapper).batchUpdateFirstReplyCommentIds(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(101L, captor.getValue().get(0).getId());
        assertEquals(1001L, captor.getValue().get(0).getFirstReplyCommentId());
        verify(commentDOMapper, never()).updateFirstReplyCommentIdByPrimaryKey(anyLong(), anyLong());
    }

    @Test
    void shouldSkipWhenFirstReplyMarkedInRedis() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList("1"));

        handler.handlePublish(publishEventWithTwoLevel(101L));

        verify(commentDOMapper, never()).batchUpdateFirstReplyCommentIds(anyList());
    }

    @Test
    void shouldSkipWhenNoEarliestReplyFound() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(List.of(
                CommentDO.builder().id(101L).firstReplyCommentId(0L).build()));
        when(commentDOMapper.selectEarliestFirstReplyByParentIds(List.of(101L)))
                .thenReturn(Collections.emptyList());

        handler.handlePublish(publishEventWithTwoLevel(101L));

        verify(commentDOMapper, never()).batchUpdateFirstReplyCommentIds(anyList());
    }

    @Test
    void shouldSyncAlreadyFilledReplyMarkAndSkipBatch() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(List.of(
                CommentDO.builder().id(101L).firstReplyCommentId(7L).build()));

        handler.handlePublish(publishEventWithTwoLevel(101L));

        verify(commentDOMapper, never()).batchUpdateFirstReplyCommentIds(anyList());
    }

    // —— 热度 ——

    @Test
    void shouldSubmitHeatForTwoLevelParents() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList("1"));

        handler.handlePublish(publishEventWithTwoLevel(101L));

        verify(commentHeatAggregator).submit(java.util.Set.of(101L));
    }

    private CommentChangedEventMqDTO event(Integer changeType, List<CommentItemMqDTO> items) {
        return CommentChangedEventMqDTO.builder()
                .changeType(changeType)
                .items(items)
                .build();
    }

    private CommentItemMqDTO item(Long id, Integer level, Long parentId) {
        return CommentItemMqDTO.builder()
                .id(id)
                .noteId(10L)
                .level(level)
                .parentId(parentId)
                .userId(100L)
                .createTime(LocalDateTime.of(2026, 8, 16, 12, 0))
                .build();
    }

    private CommentChangedEventMqDTO publishEventWithTwoLevel(Long parentId) {
        return CommentChangedEventMqDTO.builder()
                .changeType(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH)
                .items(List.of(CommentItemMqDTO.builder()
                        .id(1001L)
                        .noteId(200L)
                        .level(CommentLevelEnum.TWO.getCode())
                        .parentId(parentId)
                        .createTime(LocalDateTime.of(2026, 8, 21, 12, 0))
                        .build()))
                .build();
    }
}
