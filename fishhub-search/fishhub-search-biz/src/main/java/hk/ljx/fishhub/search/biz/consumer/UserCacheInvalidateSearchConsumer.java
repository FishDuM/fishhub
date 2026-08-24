package hk.ljx.fishhub.search.biz.consumer;

import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.framework.common.constant.MqTopicConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 用户资料变更广播消费：主动失效搜索服务当前 JVM 节点的 UserClient 本地 Caffeine 缓存
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_search_user_cache_invalidate",
        topic = MqTopicConstants.TOPIC_USER_CHANGED,
        messageModel = MessageModel.BROADCASTING
)
public class UserCacheInvalidateSearchConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(UserCacheInvalidateSearchConsumer.class);

    @Override
    public void onMessage(String body) {
        if (StringUtils.isBlank(body)) {
            return;
        }
        try {
            Long userId = Long.valueOf(body.trim());
            UserClient.invalidate(userId);
            log.info("==> 搜索服务成功失效本地用户缓存, userId: {}", userId);
        } catch (Exception e) {
            log.warn("==> 搜索服务解析用户缓存失效消息失败, body: {}", body, e);
        }
    }
}
