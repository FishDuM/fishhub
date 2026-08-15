package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.model.dto.InvalidateOneLevelCommentCacheMqDTO;
import hk.ljx.framework.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvalidateOneLevelCommentCacheConsumerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @InjectMocks
    private InvalidateOneLevelCommentCacheConsumer consumer;

    @Test
    void shouldAdvanceVersionAndEvictHotCommentList() {
        Long noteId = 100L;
        String versionKey = RedisKeyConstants.buildOneLevelCommentTotalCacheVersionKey(noteId);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(versionKey)).thenReturn(8L);

        consumer.onMessage(JsonUtils.toJsonString(InvalidateOneLevelCommentCacheMqDTO.builder()
                .eventId("event-100")
                .noteId(noteId)
                .build()));

        verify(redisTemplate).expire(versionKey,
                RedisKeyConstants.ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
        verify(redisTemplate).delete(RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId, "7"));
        verify(redisTemplate).delete(List.of(RedisKeyConstants.buildCommentListKey(noteId)));
    }
}
