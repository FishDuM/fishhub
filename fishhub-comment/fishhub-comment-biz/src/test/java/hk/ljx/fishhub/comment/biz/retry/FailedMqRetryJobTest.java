package hk.ljx.fishhub.comment.biz.retry;

import hk.ljx.fishhub.comment.biz.domain.dataobject.MqSendFailureDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.MqSendFailureMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailedMqRetryJobTest {

    @Mock
    private MqSendFailureMapper mqSendFailureMapper;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private FailedMqRetryJob retryJob;

    @Test
    void shouldDeleteFailureAfterSuccessfulRetry() {
        MqSendFailureDO failure = MqSendFailureDO.builder()
                .id(1L)
                .topic("PublishCommentTopic")
                .body("{\"commentId\":1}")
                .retryCount(0)
                .build();
        when(mqSendFailureMapper.selectRetryable(any(LocalDateTime.class), any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(failure));
        when(mqSendFailureMapper.claim(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);

        retryJob.retryFailedMessages();

        verify(rocketMQTemplate).syncSend(eq("PublishCommentTopic"), any(Message.class));
        verify(mqSendFailureMapper).deleteById(1L);
        verify(mqSendFailureMapper, never()).releaseForRetry(any(), any(Integer.class), any(), any());
    }

    @Test
    void shouldReleaseFailureWithNextRetryAfterSendException() {
        MqSendFailureDO failure = MqSendFailureDO.builder()
                .id(2L)
                .topic("CountNoteCommentTopic")
                .body("[]")
                .retryCount(2)
                .build();
        when(mqSendFailureMapper.selectRetryable(any(LocalDateTime.class), any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(failure));
        when(mqSendFailureMapper.claim(eq(2L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        doThrow(new RuntimeException("name server unavailable"))
                .when(rocketMQTemplate).syncSend(eq("CountNoteCommentTopic"), any(Message.class));

        retryJob.retryFailedMessages();

        verify(mqSendFailureMapper).releaseForRetry(
                eq(2L), eq(3), any(LocalDateTime.class), eq("name server unavailable"));
        verify(mqSendFailureMapper, never()).deleteById(2L);
    }
}
