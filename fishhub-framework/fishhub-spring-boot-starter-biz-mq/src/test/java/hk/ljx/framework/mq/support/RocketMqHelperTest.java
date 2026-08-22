package hk.ljx.framework.mq.support;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RocketMqHelperTest {

    @Test
    void asyncSendWithRetryShouldRollbackWhenAsyncSendThrowsAndSyncRetryFails() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        doThrow(new RuntimeException("async fail")).when(template).asyncSend(anyString(), any(Object.class), any(SendCallback.class));
        doThrow(new RuntimeException("sync fail")).when(template).syncSend(anyString(), any(Object.class));

        AtomicBoolean rolledBack = new AtomicBoolean(false);
        RocketMqHelper.asyncSendWithRetry(template, "topic:tag", "payload", "biz", () -> rolledBack.set(true));

        assertTrue(rolledBack.get());
    }

    @Test
    void asyncSendOrderlyWithRetryShouldRollbackWhenSyncRetryFails() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        doThrow(new RuntimeException("sync fail")).when(template).syncSendOrderly(anyString(), any(Object.class), anyString());

        AtomicBoolean rolledBack = new AtomicBoolean(false);
        ArgumentCaptor<SendCallback> callbackCaptor = ArgumentCaptor.forClass(SendCallback.class);
        RocketMqHelper.asyncSendOrderlyWithRetry(template, "topic:tag", "payload", "hash", "biz",
                () -> rolledBack.set(true));

        verify(template).asyncSendOrderly(eq("topic:tag"), eq("payload"), eq("hash"), callbackCaptor.capture());
        callbackCaptor.getValue().onException(new RuntimeException("async fail"));
        verify(template).syncSendOrderly(eq("topic:tag"), eq("payload"), eq("hash"));

        assertTrue(rolledBack.get());
    }

    @Test
    void asyncSendOrderlyWithRetryShouldRollbackWhenAsyncSendThrowsAndSyncRetryFails() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        doThrow(new RuntimeException("async fail"))
                .when(template).asyncSendOrderly(anyString(), any(Object.class), anyString(), any(SendCallback.class));
        doThrow(new RuntimeException("sync fail")).when(template).syncSendOrderly(anyString(), any(Object.class), anyString());

        AtomicBoolean rolledBack = new AtomicBoolean(false);
        RocketMqHelper.asyncSendOrderlyWithRetry(template, "topic:tag", "payload", "hash", "biz",
                () -> rolledBack.set(true));

        assertTrue(rolledBack.get());
    }

    @Test
    void asyncSendWithRetryShouldSendSpringMessagePayload() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        RocketMqHelper.asyncSendWithRetry(template, "topic:tag",
                MessageBuilder.withPayload("payload").build(), "biz", null);

        verify(template).asyncSend(eq("topic:tag"), payloadCaptor.capture(), any(SendCallback.class));
        assertEquals("payload", payloadCaptor.getValue());
    }

    @Test
    void asyncSendShouldSendSpringMessagePayload() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        RocketMqHelper.asyncSend(template, "topic:tag",
                MessageBuilder.withPayload("payload").build(), "biz");

        verify(template).asyncSend(eq("topic:tag"), payloadCaptor.capture(), any(SendCallback.class));
        assertEquals("payload", payloadCaptor.getValue());
    }

    @Test
    void asyncSendOrderlyWithRetryShouldSendSpringMessagePayload() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        RocketMqHelper.asyncSendOrderlyWithRetry(template, "topic:tag",
                MessageBuilder.withPayload("payload").build(), "hash", "biz", null);

        verify(template).asyncSendOrderly(eq("topic:tag"), payloadCaptor.capture(), eq("hash"), any(SendCallback.class));
        assertEquals("payload", payloadCaptor.getValue());
    }
}
