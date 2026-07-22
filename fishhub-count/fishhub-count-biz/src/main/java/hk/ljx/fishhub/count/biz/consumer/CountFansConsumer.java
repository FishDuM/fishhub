package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.fishhub.count.biz.constant.MQConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 计数 - 粉丝数
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_FANS,
        topic = MQConstants.TOPIC_COUNT_FANS
        )
@Slf4j
public class CountFansConsumer implements RocketMQListener<String> {
    // TODO: Message

    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 粉丝数】, {}...", body);
    }


}