package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountNoteLike2DBConsumerTest {

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
    private CountNoteLike2DBConsumer consumer;

    @Test
    void shouldApplyWholeAggregatedBatchInOneIdempotentTransaction() {
        String body = JsonUtils.toJsonString(List.of(
                AggregationCountLikeUnlikeNoteMqDTO.builder().noteId(10L).creatorId(100L).count(1).batchId("b1").build(),
                AggregationCountLikeUnlikeNoteMqDTO.builder().noteId(20L).creatorId(200L).count(-1).batchId("b1").build()));
        when(mqIdempotentExecutor.execute(eq("count-note-like-2db"), eq(body), any())).thenAnswer(inv -> {
            Runnable action = inv.getArgument(2);
            action.run();
            return true;
        });

        consumer.onMessage(body);

        // 只开 1 个幂等事务，事务内逐条 upsert 整批 delta
        verify(mqIdempotentExecutor, times(1)).execute(anyString(), anyString(), any());
        verify(noteCountDOMapper).insertOrUpdateLikeTotalByNoteId(1, 10L);
        verify(noteCountDOMapper).insertOrUpdateLikeTotalByNoteId(-1, 20L);
        verify(userCountDOMapper).insertOrUpdateLikeTotalByUserId(1, 100L);
        verify(userCountDOMapper).insertOrUpdateLikeTotalByUserId(-1, 200L);
        verify(stringRedisTemplate).delete(List.of("count:note:10", "count:note:20"));
        verify(userCountCacheVersionService).advanceVersion(100L);
        verify(userCountCacheVersionService).advanceVersion(200L);
    }
}
