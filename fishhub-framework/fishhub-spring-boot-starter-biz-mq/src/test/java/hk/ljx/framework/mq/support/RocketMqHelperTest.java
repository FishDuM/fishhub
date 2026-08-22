package hk.ljx.framework.mq.support;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RocketMqHelperTest {

    @Test
    void syncSendShouldSendRawPayload() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        RocketMqHelper.syncSend(template, "topic:tag", "payload", "biz");

        verify(template).syncSend(eq("topic:tag"), payloadCaptor.capture());
        assertEquals("payload", payloadCaptor.getValue());
    }

    @Test
    void syncSendShouldUnwrapSpringMessagePayload() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        RocketMqHelper.syncSend(template, "topic:tag",
                MessageBuilder.withPayload("payload").build(), "biz");

        verify(template).syncSend(eq("topic:tag"), payloadCaptor.capture());
        assertEquals("payload", payloadCaptor.getValue());
    }

    @Test
    void syncSendShouldThrowWhenBrokerFails() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        doThrow(new RuntimeException("broker down")).when(template).syncSend(anyString(), any(Object.class));

        assertThrows(IllegalStateException.class,
                () -> RocketMqHelper.syncSend(template, "topic:tag", "payload", "biz"));
    }

    @Test
    void syncSendOrderlyShouldSendWithHashKey() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        RocketMqHelper.syncSendOrderly(template, "topic:tag", "payload", "hash", "biz");

        verify(template).syncSendOrderly(eq("topic:tag"), payloadCaptor.capture(), eq("hash"));
        assertEquals("payload", payloadCaptor.getValue());
    }

    @Test
    void syncSendOrderlyShouldUnwrapSpringMessagePayload() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        RocketMqHelper.syncSendOrderly(template, "topic:tag",
                MessageBuilder.withPayload("payload").build(), "hash", "biz");

        verify(template).syncSendOrderly(eq("topic:tag"), payloadCaptor.capture(), eq("hash"));
        assertEquals("payload", payloadCaptor.getValue());
    }

    @Test
    void syncSendOrderlyShouldThrowWhenBrokerFails() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        doThrow(new RuntimeException("broker down"))
                .when(template).syncSendOrderly(anyString(), any(Object.class), anyString());

        assertThrows(IllegalStateException.class,
                () -> RocketMqHelper.syncSendOrderly(template, "topic:tag", "payload", "hash", "biz"));
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
}
