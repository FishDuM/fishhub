package hk.ljx.fishhub.user.relation.biz.consumer;

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
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.framework.mq.tx.TxLocalTransaction;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
    private RelationListCacheService relationListCacheService;
    @Mock
    private TransactionalMqSender transactionalMqSender;
    @Mock
    private TxJournalStore txJournalStore;
    @InjectMocks
    private FollowUnfollowConsumer consumer;

    @Test
    void shouldMaintainFansAndSendCountEventWhenFollowCreatesRelation() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(followingDOMapper.insertIgnore(any())).thenReturn(1);
        doAnswer(invocation -> {
            TxLocalTransaction action = invocation.getArgument(2);
            action.execute("test-tx-id");
            return null;
        }).when(transactionalMqSender).sendInTransaction(anyString(), anyString(), any());

        consumer.onMessage(message(MQConstants.TAG_FOLLOW,
                JsonUtils.toJsonString(FollowUserMqDTO.builder()
                        .userId(1L)
                        .followUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        // 反向粉丝 ZSet 增量维护 + 事务消息发送 + 事务登记
        verify(relationListCacheService).addFan(anyLong(), anyLong(), any(LocalDateTime.class));
        verify(transactionalMqSender).sendInTransaction(anyString(), anyString(), any());
        verify(txJournalStore).record("test-tx-id");
    }

    @Test
    void shouldNotMaintainFansWhenFollowAlreadyExists() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        // 重复投递 / 已关注：insertIgnore 返回 0
        when(followingDOMapper.insertIgnore(any())).thenReturn(0);
        doAnswer(invocation -> {
            TxLocalTransaction action = invocation.getArgument(2);
            action.execute("test-tx-id");
            return null;
        }).when(transactionalMqSender).sendInTransaction(anyString(), anyString(), any());

        consumer.onMessage(message(MQConstants.TAG_FOLLOW,
                JsonUtils.toJsonString(FollowUserMqDTO.builder()
                        .userId(1L)
                        .followUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        // 状态未变化：不登记事务，不维护粉丝
        verify(relationListCacheService).addFan(anyLong(), anyLong(), any(LocalDateTime.class));
        verify(txJournalStore, never()).record(anyString());
    }

    @Test
    void shouldMaintainFansAndSendCountEventWhenUnfollowRemovesRelation() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(followingDOMapper.deleteByUserIdAndFollowingUserId(1L, 2L)).thenReturn(1);
        doAnswer(invocation -> {
            TxLocalTransaction action = invocation.getArgument(2);
            action.execute("test-tx-id");
            return null;
        }).when(transactionalMqSender).sendInTransaction(anyString(), anyString(), any());

        consumer.onMessage(message(MQConstants.TAG_UNFOLLOW,
                JsonUtils.toJsonString(UnfollowUserMqDTO.builder()
                        .userId(1L)
                        .unfollowUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(relationListCacheService).removeFan(anyLong(), anyLong());
        verify(transactionalMqSender).sendInTransaction(anyString(), anyString(), any());
        verify(txJournalStore).record("test-tx-id");
    }

    @Test
    void shouldNotMaintainFansWhenUnfollowDidNotRemoveRelation() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(followingDOMapper.deleteByUserIdAndFollowingUserId(1L, 2L)).thenReturn(0);
        doAnswer(invocation -> {
            TxLocalTransaction action = invocation.getArgument(2);
            action.execute("test-tx-id");
            return null;
        }).when(transactionalMqSender).sendInTransaction(anyString(), anyString(), any());

        consumer.onMessage(message(MQConstants.TAG_UNFOLLOW,
                JsonUtils.toJsonString(UnfollowUserMqDTO.builder()
                        .userId(1L)
                        .unfollowUserId(2L)
                        .createTime(LocalDateTime.now())
                        .build())));

        verify(relationListCacheService).removeFan(anyLong(), anyLong());
        verify(txJournalStore, never()).record(anyString());
    }

    private Message message(String tag, String payload) {
        Message message = new Message();
        message.setTags(tag);
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
