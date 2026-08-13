package hk.ljx.fishhub.user.biz.consumer;

import hk.ljx.fishhub.user.biz.constant.MQConstants;
import hk.ljx.fishhub.user.biz.service.UserService;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_DELETE_USER_LOCAL_CACHE,
        topic = MQConstants.TOPIC_DELETE_USER_LOCAL_CACHE,
        messageModel = MessageModel.BROADCASTING)
public class DeleteUserLocalCacheConsumer implements RocketMQListener<String> {

    @Resource
    private UserService userService;

    @Override
    public void onMessage(String body) {
        userService.deleteUserLocalCache(Long.valueOf(body));
    }
}
