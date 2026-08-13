package hk.ljx.fishhub.count.biz.util;

import cn.hutool.crypto.digest.DigestUtil;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;

public final class RocketMqMessageUtils {

    private RocketMqMessageUtils() {
    }

    public static String body(MessageExt message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }

    public static String stableIdentity(MessageExt message) {
        // 幂等键基于业务内容（topic + body），补发/重投的新消息 msgId 会变，payload 不变
        return DigestUtil.sha256Hex(message.getTopic() + ":" + body(message));
    }
}
