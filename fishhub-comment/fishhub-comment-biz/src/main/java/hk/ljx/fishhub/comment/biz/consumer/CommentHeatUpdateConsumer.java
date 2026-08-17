package hk.ljx.fishhub.comment.biz.consumer;

import com.google.common.collect.Sets;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.service.CommentHeatAggregator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 消费评论点赞聚合落库后的热度重算事件（Set<评论 ID>），交给聚合器合并重算。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_HEAT_UPDATE,
        topic = MQConstants.TOPIC_COMMENT_HEAT_UPDATE)
@Slf4j
public class CommentHeatUpdateConsumer implements RocketMQListener<String> {

    @Resource
    private CommentHeatAggregator commentHeatAggregator;

    @Override
    public void onMessage(String body) {
        Set<Long> commentIds = Sets.newHashSet();
        try {
            commentIds.addAll(JsonUtils.parseSet(body, Long.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("评论热度消息格式错误", e);
        }
        commentHeatAggregator.submit(commentIds);
    }
}
