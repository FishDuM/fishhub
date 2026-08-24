package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.constant.MqTopicConstants;
import hk.ljx.fishhub.user.client.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 监听用户资料变更广播，主动失效笔记服务本地 JVM 内存中的用户缓存
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_note_user_cache_invalidate",
        topic = MqTopicConstants.TOPIC_USER_CHANGED,
        messageModel = MessageModel.BROADCASTING
)
@Slf4j
public class UserCacheInvalidateNoteConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String body) {
        if (StringUtils.isBlank(body)) {
            return;
        }
        try {
            Long userId = Long.valueOf(body.trim());
            UserClient.invalidate(userId);
            log.info("笔记服务收到用户资料变更广播，已失效本地用户缓存, userId={}", userId);
        } catch (Exception e) {
            log.warn("笔记服务失效本地用户缓存异常, body={}", body, e);
        }
    }
}
