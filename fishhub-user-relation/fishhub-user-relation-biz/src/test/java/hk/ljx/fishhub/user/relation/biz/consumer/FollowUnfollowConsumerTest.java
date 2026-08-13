package hk.ljx.fishhub.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FansDOMapper;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.MqConsumeRecordMapper;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.retry.ReliableMqOutbox;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUnfollowConsumerTest {

    @Mock
    private FollowingDOMapper followingDOMapper;
    @Mock
    private FansDOMapper fansDOMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private MqConsumeRecordMapper mqConsumeRecordMapper;
    @Mock
    private ReliableMqOutbox reliableMqOutbox;
    @InjectMocks
    private FollowUnfollowConsumer consumer;

    @Test
    void shouldNotEmitCountWhenFollowDidNotCreateRelation() {
        executeTransactionCallback();
        when(mqConsumeRecordMapper.insert(any(), any())).thenReturn(1);
        when(followingDOMapper.insertIgnore(any())).thenReturn(0);

        consumer.onMessage(message(MQConstants.TAG_FOLLOW,
                JsonUtils.toJsonString(FollowUserMqDTO.builder()
                        .userId(1L)
                        .followUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(fansDOMapper).insertIgnore(any());
        verify(redisTemplate, never()).execute(any(), any(), any());
        verifyNoInteractions(reliableMqOutbox);
    }

    @Test
    void shouldRepairFansMirrorWithoutEmittingCountWhenUnfollowDidNotRemoveRelation() {
        executeTransactionCallback();
        when(mqConsumeRecordMapper.insert(any(), any())).thenReturn(1);
        when(followingDOMapper.deleteByUserIdAndFollowingUserId(1L, 2L)).thenReturn(0);

        consumer.onMessage(message(MQConstants.TAG_UNFOLLOW,
                JsonUtils.toJsonString(UnfollowUserMqDTO.builder()
                        .userId(1L)
                        .unfollowUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(fansDOMapper).deleteByUserIdAndFansUserId(2L, 1L);
        verify(redisTemplate, never()).opsForZSet();
        verifyNoInteractions(reliableMqOutbox);
    }

    @Test
    void shouldPersistAndSendBothCountEventsWhenFollowCreatesRelation() {
        executeTransactionCallback();
        when(mqConsumeRecordMapper.insert(any(), any())).thenReturn(1);
        when(followingDOMapper.insertIgnore(any())).thenReturn(1);

        consumer.onMessage(message(MQConstants.TAG_FOLLOW,
                JsonUtils.toJsonString(FollowUserMqDTO.builder()
                        .userId(1L)
                        .followUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(reliableMqOutbox, times(2)).enqueue(anyString(), anyString(), anyString());
        verify(reliableMqOutbox, times(2)).sendNow(anyString(), anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    private void executeTransactionCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

    private Message message(String tag, String payload) {
        Message message = new Message();
        message.setTags(tag);
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
