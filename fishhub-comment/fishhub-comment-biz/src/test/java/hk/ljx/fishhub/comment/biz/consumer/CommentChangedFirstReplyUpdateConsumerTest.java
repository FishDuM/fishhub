package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.bo.CommentFirstReplyBO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentItemMqDTO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentChangedFirstReplyUpdateConsumerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Mock
    private CommentDOMapper commentDOMapper;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @InjectMocks
    private CommentChangedFirstReplyUpdateConsumer consumer;

    @Test
    void shouldBatchFillFirstReplyForPendingComments() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(List.of(
                CommentDO.builder().id(101L).firstReplyCommentId(0L).build()));
        when(commentDOMapper.selectEarliestFirstReplyByParentIds(List.of(101L))).thenReturn(List.of(
                CommentDO.builder().id(1001L).parentId(101L).build()));

        consumer.onMessage(publishEventWithTwoLevel(101L));

        ArgumentCaptor<List<CommentFirstReplyBO>> captor = ArgumentCaptor.forClass(List.class);
        verify(commentDOMapper).batchUpdateFirstReplyCommentIds(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(101L, captor.getValue().get(0).getId());
        assertEquals(1001L, captor.getValue().get(0).getFirstReplyCommentId());
        // 不再走逐条回填
        verify(commentDOMapper, never()).updateFirstReplyCommentIdByPrimaryKey(anyLong(), anyLong());
    }

    @Test
    void shouldSkipWhenFirstReplyMarkedInRedis() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList("1"));

        consumer.onMessage(publishEventWithTwoLevel(101L));

        verify(commentDOMapper, never()).batchUpdateFirstReplyCommentIds(anyList());
    }

    @Test
    void shouldSkipWhenNoEarliestReplyFound() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(List.of(
                CommentDO.builder().id(101L).firstReplyCommentId(0L).build()));
        when(commentDOMapper.selectEarliestFirstReplyByParentIds(List.of(101L)))
                .thenReturn(Collections.emptyList());

        consumer.onMessage(publishEventWithTwoLevel(101L));

        verify(commentDOMapper, never()).batchUpdateFirstReplyCommentIds(anyList());
    }

    @Test
    void shouldSyncAlreadyFilledReplyMarkAndSkipBatch() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(List.of(
                CommentDO.builder().id(101L).firstReplyCommentId(7L).build()));

        consumer.onMessage(publishEventWithTwoLevel(101L));

        // first_reply_comment_id 已非 0：只走 Redis 标记同步，不再批量回填
        verify(commentDOMapper, never()).batchUpdateFirstReplyCommentIds(anyList());
    }

    private String publishEventWithTwoLevel(Long parentId) {
        return JsonUtils.toJsonString(CommentChangedEventMqDTO.builder()
                .changeType(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH)
                .items(List.of(CommentItemMqDTO.builder()
                        .id(1001L)
                        .noteId(200L)
                        .level(CommentLevelEnum.TWO.getCode())
                        .parentId(parentId)
                        .createTime(LocalDateTime.of(2026, 8, 21, 12, 0))
                        .build()))
                .build());
    }

}
