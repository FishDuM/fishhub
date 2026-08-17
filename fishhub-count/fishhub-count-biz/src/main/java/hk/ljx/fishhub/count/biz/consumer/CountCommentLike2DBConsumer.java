package hk.ljx.fishhub.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.count.biz.service.MqIdempotentExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB, // Group 组
        topic = MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB // 主题 Topic
        )
@Slf4j
public class CountCommentLike2DBConsumer implements RocketMQListener<String> {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    // 每批只 acquire 一次，限速 20000 安全
    private RateLimiter rateLimiter = RateLimiter.create(20000);

    @Override
    public void onMessage(String body) {
        // 流量削峰，无令牌时阻塞
        rateLimiter.acquire();

        log.info("## 消费到了 MQ 【计数: 评论点赞数入库】, {}...", body);

        List<AggregationCountLikeUnlikeCommentMqDTO> countList = null;
        try {
            countList = JsonUtils.parseList(body, AggregationCountLikeUnlikeCommentMqDTO.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isEmpty(countList) || countList.stream().anyMatch(item -> item.getCommentId() == null
                || item.getCount() == null || item.getBatchId() == null)) {
            throw new IllegalArgumentException("评论点赞计数消息为空或格式错误");
        }
        List<AggregationCountLikeUnlikeCommentMqDTO> finalCountList = countList;
        boolean applied = mqIdempotentExecutor.execute("count-comment-like-2db", body, () -> {
            finalCountList.forEach(item -> {
                Long commentId = item.getCommentId();
                Integer count = item.getCount();
                commentDOMapper.updateLikeTotalByCommentId(count, commentId);
            });
        });
        stringRedisTemplate.delete(countList.stream()
                .map(item -> RedisKeyConstants.buildCountCommentKey(item.getCommentId()))
                .distinct()
                .toList());

        // 无论是否重复投递都重算热度，消费者按库值重算，重投安全
        Set<Long> commentIds = countList.stream()
                .map(AggregationCountLikeUnlikeCommentMqDTO::getCommentId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        rocketMQTemplate.syncSend(MQConstants.TOPIC_COMMENT_HEAT_UPDATE,
                MessageBuilder.withPayload(JsonUtils.toJsonString(commentIds)).build());
        if (!applied) {
            log.info("评论点赞计数消息已处理，忽略重复投递");
        }
    }

}
