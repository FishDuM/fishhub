package hk.ljx.fishhub.user.relation.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.model.dto.CountFollowUnfollowMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 关注关系变更事件分发器：将统一事件扇出到 following / fans 两个既有计数 topic。
 * 消息体与 CountFollowUnfollowMqDTO 契约一致且保持确定性，
 * broker 重投导致的重复扇出由下游幂等（batchId / 日增量去重）吸收。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_USER_RELATION_CHANGED,
        topic = MQConstants.TOPIC_USER_RELATION_CHANGED)
@Slf4j
public class UserRelationChangedDispatcherConsumer implements RocketMQListener<String> {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        CountFollowUnfollowMqDTO event = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
        if (event == null || event.getUserId() == null || event.getTargetUserId() == null
                || event.getType() == null || event.getCreateTime() == null) {
            throw new IllegalArgumentException("关注关系变更消息缺少必要字段");
        }

        // 任一扇出失败即抛出，由 broker 重投整体重试（下游幂等）。
        // hashKey 按计数归属维度选 queue：following_total 归关注者、fans_total 归被关注者，
        // 与下游 ConsumeMode.ORDERLY 配合恢复同用户事件的队列内有序。
        rocketMQTemplate.syncSendOrderly(MQConstants.TOPIC_COUNT_FOLLOWING,
                MessageBuilder.withPayload(body).build(), String.valueOf(event.getUserId()));
        rocketMQTemplate.syncSendOrderly(MQConstants.TOPIC_COUNT_FANS,
                MessageBuilder.withPayload(body).build(), String.valueOf(event.getTargetUserId()));
        log.info("关注关系变更事件已扇出, userId={}, targetUserId={}, type={}",
                event.getUserId(), event.getTargetUserId(), event.getType());
    }
}
