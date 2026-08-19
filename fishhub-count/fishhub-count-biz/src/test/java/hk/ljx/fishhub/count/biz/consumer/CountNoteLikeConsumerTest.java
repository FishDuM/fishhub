package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.consumer.aggregation.AbstractNoteCountAggregationConsumer;
import hk.ljx.fishhub.count.biz.model.dto.CountNoteMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试：点赞批量消费者发出的 payload，聚合端直接落库并失效缓存，无需二次 MQ。
 */
@ExtendWith(MockitoExtension.class)
class CountNoteLikeConsumerTest {

    @Mock
    private NoteCountDOMapper noteCountDOMapper;
    @Mock
    private UserCountDOMapper userCountDOMapper;
    @Mock
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private UserCountCacheVersionService userCountCacheVersionService;

    @InjectMocks
    private CountNoteLikeConsumer consumer;

    @Test
    void shouldAggregateAndDirectlyPersistToDb() throws Exception {
        // 同 note 两赞 + 另一 note 一取消的数组 payload
        String body = JsonUtils.toJsonString(List.of(
                event(1L, 100L, 7L, 1),
                event(2L, 100L, 7L, 1),
                event(3L, 200L, 8L, 0)));

        when(mqIdempotentExecutor.execute(eq("count-note-like"), anyString(), any())).thenAnswer(inv -> {
            Runnable action = inv.getArgument(2);
            action.run();
            return true;
        });

        invokeConsumeMessage(body);

        verify(noteCountDOMapper).insertOrUpdateLikeTotalByNoteId(2, 100L);
        verify(noteCountDOMapper).insertOrUpdateLikeTotalByNoteId(-1, 200L);
        verify(userCountDOMapper).insertOrUpdateLikeTotalByUserId(2, 7L);
        verify(userCountDOMapper).insertOrUpdateLikeTotalByUserId(-1, 8L);
        verify(stringRedisTemplate).delete(List.of("count:note:100", "count:note:200"));
        verify(userCountCacheVersionService).advanceVersion(7L);
        verify(userCountCacheVersionService).advanceVersion(8L);
    }

    private CountNoteMqDTO event(Long userId, Long noteId, Long creatorId, Integer type) {
        return CountNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .noteCreatorId(creatorId)
                .type(type)
                .createTime(LocalDateTime.of(2026, 8, 16, 12, 0))
                .build();
    }

    private void invokeConsumeMessage(String body) throws Exception {
        java.lang.reflect.Method m = AbstractNoteCountAggregationConsumer.class.getDeclaredMethod("consumeBatches", List.class);
        m.setAccessible(true);
        m.invoke(consumer, List.of(body));
    }
}
