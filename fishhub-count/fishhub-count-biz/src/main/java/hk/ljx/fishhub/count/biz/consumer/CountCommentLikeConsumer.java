package hk.ljx.fishhub.count.biz.consumer;

import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountLikeUnlikeCommentMqDTO;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评论点赞/取消点赞事件的批量聚合消费者（与 {@link CountNoteLikeConsumer} 同款）。
 *
 * <p>手动 {@link DefaultMQPushConsumer} 接收整批消息，聚合成每 commentId 一条 delta
 * 后只发 1 条消息给 2DB 消费者（1 个事务落库）。
 */
@Component
@Slf4j
public class CountCommentLikeConsumer {

    /** 每批最多拉取的消息数 */
    private static final int CONSUME_BATCH_MAX_SIZE = 30;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    private DefaultMQPushConsumer consumer;

    @Bean
    public DefaultMQPushConsumer countCommentLikePushConsumer() throws MQClientException {
        String group = "fishhub_group_" + MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE;
        consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.subscribe(MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE, "*");
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(CONSUME_BATCH_MAX_SIZE);
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            try {
                List<String> bodys = msgs.stream()
                        .map(msg -> new String(msg.getBody(), StandardCharsets.UTF_8))
                        .toList();
                consumeMessage(bodys);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                log.error("评论点赞计数聚合消费失败，整批稍后重投", e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        consumer.start();
        return consumer;
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【评论点赞数】聚合消息, size: {}", bodys.size());

        // 聚合批次标识：同批重投时内容不变
        String batchId = cn.hutool.crypto.digest.DigestUtil.sha256Hex(String.join("|", bodys));

        // List<String> 转 List<CountLikeUnlikeCommentMqDTO>
        List<CountLikeUnlikeCommentMqDTO> countLikeUnlikeCommentMqDTOS = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountLikeUnlikeCommentMqDTO.class)).toList();
        if (countLikeUnlikeCommentMqDTOS.stream().anyMatch(item -> item == null || item.getCommentId() == null
                || LikeUnlikeCommentTypeEnum.valueOf(item.getType()) == null)) {
            throw new IllegalArgumentException("评论点赞计数消息缺少必要字段或操作类型无效");
        }

        // 按评论 ID 进行分组
        Map<Long, List<CountLikeUnlikeCommentMqDTO>> groupMap = countLikeUnlikeCommentMqDTOS.stream()
                .collect(Collectors.groupingBy(CountLikeUnlikeCommentMqDTO::getCommentId));

        // 按组汇总数据，统计出最终的计数
        // 最终操作的计数对象
        List<AggregationCountLikeUnlikeCommentMqDTO> countList = Lists.newArrayList();

        for (Map.Entry<Long, List<CountLikeUnlikeCommentMqDTO>> entry : groupMap.entrySet()) {
            // 评论 ID
            Long commentId = entry.getKey();

            List<CountLikeUnlikeCommentMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountLikeUnlikeCommentMqDTO countLikeUnlikeCommentMqDTO : list) {
                // 获取操作类型
                Integer type = countLikeUnlikeCommentMqDTO.getType();

                // 根据操作类型，获取对应枚举
                LikeUnlikeCommentTypeEnum likeUnlikeCommentTypeEnum = LikeUnlikeCommentTypeEnum.valueOf(type);

                switch (likeUnlikeCommentTypeEnum) {
                    case LIKE -> finalCount += 1; // 如果为点赞操作，点赞数 +1
                    case UNLIKE -> finalCount -= 1; // 如果为取消点赞操作，点赞数 -1
                }
            }
            // 将分组后统计出的最终计数，存入 countList 中
            countList.add(AggregationCountLikeUnlikeCommentMqDTO.builder()
                    .commentId(commentId)
                    .count(finalCount)
                    .batchId(batchId)
                    .build());
        }

        log.info("## 【评论点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 整批只发 1 条消息给 2DB 落库
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();
        rocketMQTemplate.syncSend(MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB, message);
    }

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("评论点赞聚合消费者关闭失败", e);
            }
        }
    }
}
