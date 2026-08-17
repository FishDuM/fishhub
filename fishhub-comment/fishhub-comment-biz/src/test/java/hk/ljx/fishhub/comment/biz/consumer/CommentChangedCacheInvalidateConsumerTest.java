package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentItemMqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentChangedCacheInvalidateConsumerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private CommentDetailCache commentDetailCache;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @InjectMocks
    private CommentChangedCacheInvalidateConsumer consumer;

    @Test
    void shouldAddOneLevelCommentAndChildCommentOnPublish() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(zSetOperations.zCard(anyString())).thenReturn(1L);

        consumer.onMessage(body(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH,
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

        consumer.onMessage(body(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH, List.of(item(1L, 1, 10L))));

        verify(zSetOperations).removeRange(RedisKeyConstants.buildCommentListKey(10L), 0L, -501L);
    }

    @Test
    void shouldRemoveOneLevelCommentOnDeleteAndInvalidateDetailCache() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        consumer.onMessage(body(MQConstants.COMMENT_CHANGE_TYPE_DELETE,
                List.of(item(1L, 1, 10L), item(2L, 2, 1L))));

        verify(zSetOperations).remove(RedisKeyConstants.buildCommentListKey(10L), "1");
        verify(zSetOperations).remove(RedisKeyConstants.buildChildCommentListKey(1L), "2");
        verify(stringRedisTemplate).delete(RedisKeyConstants.buildHaveFirstReplyCommentKey(1L));
        verify(commentDetailCache).delete(any());
        verifyNoMoreInteractions(commentDetailCache);
    }

    @Test
    void shouldRejectEventWithoutItems() {
        assertThrows(IllegalArgumentException.class, () -> consumer.onMessage(
                "{\"changeType\":1,\"items\":[]}"));
    }

    private String body(Integer changeType, List<CommentItemMqDTO> items) {
        return JsonUtils.toJsonString(CommentChangedEventMqDTO.builder()
                .changeType(changeType)
                .items(items)
                .build());
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
}