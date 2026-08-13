package hk.ljx.fishhub.count.biz.util;

import cn.hutool.crypto.digest.DigestUtil;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMqMessageUtilsTest {

    @Test
    void shouldStableAcrossBrokerRedelivery() {
        MessageExt message = mock(MessageExt.class);
        when(message.getTopic()).thenReturn("TestTopic");
        when(message.getBody()).thenReturn("same-payload".getBytes(StandardCharsets.UTF_8));

        assertEquals("same-payload", RocketMqMessageUtils.body(message));
        assertEquals(DigestUtil.sha256Hex("TestTopic:same-payload"), RocketMqMessageUtils.stableIdentity(message));
    }

    @Test
    void shouldIgnoreMsgIdChange() {
        MessageExt first = mock(MessageExt.class);
        when(first.getTopic()).thenReturn("TestTopic");
        when(first.getBody()).thenReturn("same-payload".getBytes(StandardCharsets.UTF_8));
        when(first.getMsgId()).thenReturn("broker-message-id-1");

        MessageExt retried = mock(MessageExt.class);
        when(retried.getTopic()).thenReturn("TestTopic");
        when(retried.getBody()).thenReturn("same-payload".getBytes(StandardCharsets.UTF_8));
        when(retried.getMsgId()).thenReturn("broker-message-id-2");

        assertEquals(RocketMqMessageUtils.stableIdentity(first), RocketMqMessageUtils.stableIdentity(retried));
    }
}
