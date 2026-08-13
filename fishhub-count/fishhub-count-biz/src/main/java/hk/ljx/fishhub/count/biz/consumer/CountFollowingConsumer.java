package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.model.dto.CountFollowUnfollowMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_FOLLOWING,
        topic = MQConstants.TOPIC_COUNT_FOLLOWING,
        consumeMode = ConsumeMode.ORDERLY)
@Slf4j
public class CountFollowingConsumer implements RocketMQListener<String> {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        if (StringUtils.isBlank(body)) {
            throw new IllegalArgumentException("关注计数消息为空");
        }

        CountFollowUnfollowMqDTO event = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
        if (event == null || event.getUserId() == null || event.getType() == null || event.getCreateTime() == null) {
            throw new IllegalArgumentException("关注计数消息缺少必要字段");
        }

        // 第一阶段只负责可靠转发。数据库消费者提交后统一失效缓存，避免 Redis 增量与 MySQL 事务分裂。
        rocketMQTemplate.syncSendOrderly(
                MQConstants.TOPIC_COUNT_FOLLOWING_2_DB,
                MessageBuilder.withPayload(body).build(), String.valueOf(event.getUserId()));
    }
}
