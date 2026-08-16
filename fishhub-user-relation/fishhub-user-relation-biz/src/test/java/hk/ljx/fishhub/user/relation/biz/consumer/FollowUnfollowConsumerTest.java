package hk.ljx.fishhub.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.framework.mq.tx.TxLocalTransaction;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FansDOMapper;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.MqConsumeRecordMapper;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private TransactionalMqSender transactionalMqSender;
    @Mock
    private TxJournalStore txJournalStore;
    @InjectMocks
    private FollowUnfollowConsumer consumer;

    @Test
    void shouldNotRecordJournalWhenFollowDidNotCreateRelation() {
        executeLocalTxThroughSender();
        when(mqConsumeRecordMapper.insert(any(), any())).thenReturn(1);
        when(followingDOMapper.insertIgnore(any())).thenReturn(0);

        consumer.onMessage(message(MQConstants.TAG_FOLLOW,
                JsonUtils.toJsonString(FollowUserMqDTO.builder()
                        .userId(1L)
                        .followUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(fansDOMapper).insertIgnore(any());
        // 关系未变化：本地事务不登记 journal，半消息由 broker 回滚丢弃
        verify(txJournalStore, never()).record(anyString());
    }

    @Test
    void shouldRepairFansMirrorWithoutJournalWhenUnfollowDidNotRemoveRelation() {
        executeLocalTxThroughSender();
        when(mqConsumeRecordMapper.insert(any(), any())).thenReturn(1);
        when(followingDOMapper.deleteByUserIdAndFollowingUserId(1L, 2L)).thenReturn(0);

        consumer.onMessage(message(MQConstants.TAG_UNFOLLOW,
                JsonUtils.toJsonString(UnfollowUserMqDTO.builder()
                        .userId(1L)
                        .unfollowUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(fansDOMapper).deleteByUserIdAndFansUserId(2L, 1L);
        verify(txJournalStore, never()).record(anyString());
    }

    @Test
    void shouldRecordJournalWhenFollowCreatesRelation() {
        executeLocalTxThroughSender();
        when(mqConsumeRecordMapper.insert(any(), any())).thenReturn(1);
        when(followingDOMapper.insertIgnore(any())).thenReturn(1);

        consumer.onMessage(message(MQConstants.TAG_FOLLOW,
                JsonUtils.toJsonString(FollowUserMqDTO.builder()
                        .userId(1L)
                        .followUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(transactionalMqSender).sendInTransaction(
                eq(MQConstants.TOPIC_USER_RELATION_CHANGED), anyString(), any());
        verify(txJournalStore).record("tx-1");
    }

    /**
     * 事务消息发送器同步执行本地事务动作（与 rocketmq-client 的真实时序一致）。
     */
    private void executeLocalTxThroughSender() {
        doAnswer(invocation -> {
            TxLocalTransaction localTx = invocation.getArgument(2);
            localTx.execute("tx-1");
            return null;
        }).when(transactionalMqSender).sendInTransaction(anyString(), anyString(), any());
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
