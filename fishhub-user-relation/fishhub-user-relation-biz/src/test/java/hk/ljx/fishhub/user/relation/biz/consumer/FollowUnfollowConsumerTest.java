package hk.ljx.fishhub.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.relation.biz.cache.RelationListCacheService;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUnfollowConsumerTest {

    @Mock
    private FollowingDOMapper followingDOMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RelationListCacheService relationListCacheService;
    @InjectMocks
    private FollowUnfollowConsumer consumer;

    @Test
    void shouldMaintainFansAndSendCountEventWhenFollowCreatesRelation() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class));
        });
        when(followingDOMapper.insertIgnore(any())).thenReturn(1);

        consumer.onMessage(message(MQConstants.TAG_FOLLOW,
                JsonUtils.toJsonString(FollowUserMqDTO.builder()
                        .userId(1L)
                        .followUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        // 反向粉丝 ZSet 增量维护 + 发计数事件
        verify(relationListCacheService).addFan(anyLong(), anyLong(), any(LocalDateTime.class));
        verify(rocketMQTemplate).syncSendOrderly(
                anyString(), any(org.springframework.messaging.Message.class), anyString());
    }

    @Test
    void shouldNotMaintainFansWhenFollowAlreadyExists() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class));
        });
        // 重复投递 / 已关注：insertIgnore 返回 0
        when(followingDOMapper.insertIgnore(any())).thenReturn(0);

        consumer.onMessage(message(MQConstants.TAG_FOLLOW,
                JsonUtils.toJsonString(FollowUserMqDTO.builder()
                        .userId(1L)
                        .followUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        // 状态未变化：不发计数事件，也不动粉丝 ZSet
        verify(relationListCacheService, never()).addFan(anyLong(), anyLong(), any(LocalDateTime.class));
        verify(rocketMQTemplate, never()).syncSendOrderly(anyString(), any(org.springframework.messaging.Message.class), anyString());
    }

    @Test
    void shouldMaintainFansAndSendCountEventWhenUnfollowRemovesRelation() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class));
        });
        when(followingDOMapper.deleteByUserIdAndFollowingUserId(1L, 2L)).thenReturn(1);

        consumer.onMessage(message(MQConstants.TAG_UNFOLLOW,
                JsonUtils.toJsonString(UnfollowUserMqDTO.builder()
                        .userId(1L)
                        .unfollowUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(relationListCacheService).removeFan(anyLong(), anyLong());
        verify(rocketMQTemplate).syncSendOrderly(
                anyString(), any(org.springframework.messaging.Message.class), anyString());
    }

    @Test
    void shouldNotMaintainFansWhenUnfollowDidNotRemoveRelation() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class));
        });
        when(followingDOMapper.deleteByUserIdAndFollowingUserId(1L, 2L)).thenReturn(0);

        consumer.onMessage(message(MQConstants.TAG_UNFOLLOW,
                JsonUtils.toJsonString(UnfollowUserMqDTO.builder()
                        .userId(1L)
                        .unfollowUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(relationListCacheService, never()).removeFan(anyLong(), anyLong());
        verify(rocketMQTemplate, never()).syncSendOrderly(anyString(), any(org.springframework.messaging.Message.class), anyString());
    }

    private Message message(String tag, String payload) {
        Message message = new Message();
        message.setTags(tag);
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
