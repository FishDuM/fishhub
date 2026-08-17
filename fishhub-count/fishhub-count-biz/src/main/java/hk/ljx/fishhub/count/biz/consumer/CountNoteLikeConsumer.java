package hk.ljx.fishhub.count.biz.consumer;

import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.enums.LikeUnlikeNoteTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountLikeUnlikeNoteMqDTO;
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
 * 笔记点赞/取消点赞事件的批量聚合消费者。
 *
 * <p>手动 {@link DefaultMQPushConsumer} 接收整批消息，聚合成每 noteId 一条 delta
 * 后只发 1 条消息给 2DB 消费者（1 个事务落库）。
 */
@Component
@Slf4j
public class CountNoteLikeConsumer {

    /** 每批最多拉取的消息数 */
    private static final int CONSUME_BATCH_MAX_SIZE = 30;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    private DefaultMQPushConsumer consumer;

    @Bean
    public DefaultMQPushConsumer countNoteLikePushConsumer() throws MQClientException {
        String group = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_LIKE;
        consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.subscribe(MQConstants.TOPIC_COUNT_NOTE_LIKE, "*");
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
                log.error("笔记点赞计数聚合消费失败，整批稍后重投", e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        consumer.start();
        return consumer;
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());

        // 聚合批次标识：同批重投时内容不变
        String batchId = cn.hutool.crypto.digest.DigestUtil.sha256Hex(String.join("|", bodys));

        // 兼容批量数组与旧版单条消息
        List<CountLikeUnlikeNoteMqDTO> countLikeUnlikeNoteMqDTOS = bodys.stream()
                .flatMap(body -> parseEvents(body).stream())
                .toList();
        if (countLikeUnlikeNoteMqDTOS.stream().anyMatch(item -> item == null || item.getNoteId() == null
                || item.getNoteCreatorId() == null
                || LikeUnlikeNoteTypeEnum.valueOf(item.getType()) == null)) {
            throw new IllegalArgumentException("笔记点赞计数消息缺少必要字段或操作类型无效");
        }

        // 按笔记 ID 进行分组
        Map<Long, List<CountLikeUnlikeNoteMqDTO>> groupMap = countLikeUnlikeNoteMqDTOS.stream()
                .collect(Collectors.groupingBy(CountLikeUnlikeNoteMqDTO::getNoteId));

        // 按组汇总数据，统计出最终的计数
        // 最终操作的计数对象
        List<AggregationCountLikeUnlikeNoteMqDTO> countList = Lists.newArrayList();

        for (Map.Entry<Long, List<CountLikeUnlikeNoteMqDTO>> entry : groupMap.entrySet()) {
            // 笔记 ID
            Long noteId = entry.getKey();
            // 笔记发布者 ID
            Long creatorId = null;
            List<CountLikeUnlikeNoteMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountLikeUnlikeNoteMqDTO countLikeUnlikeNoteMqDTO : list) {
                // 设置笔记发布者用户 ID
                creatorId = countLikeUnlikeNoteMqDTO.getNoteCreatorId();
                // 获取操作类型
                Integer type = countLikeUnlikeNoteMqDTO.getType();

                // 根据操作类型，获取对应枚举
                LikeUnlikeNoteTypeEnum likeUnlikeNoteTypeEnum = LikeUnlikeNoteTypeEnum.valueOf(type);

                switch (likeUnlikeNoteTypeEnum) {
                    case LIKE -> finalCount += 1; // 如果为点赞操作，点赞数 +1
                    case UNLIKE -> finalCount -= 1; // 如果为取消点赞操作，点赞数 -1
                }
            }
            // 将分组后统计出的最终计数，存入 countList 中
            countList.add(AggregationCountLikeUnlikeNoteMqDTO.builder()
                            .noteId(noteId)
                            .creatorId(creatorId)
                            .count(finalCount)
                            .batchId(batchId)
                            .build());
        }

        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 整批只发 1 条消息给 2DB 落库
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();
        rocketMQTemplate.syncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB, message);
    }

    private List<CountLikeUnlikeNoteMqDTO> parseEvents(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            try {
            return JsonUtils.parseList(trimmed, CountLikeUnlikeNoteMqDTO.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("笔记点赞计数消息格式错误", e);
            }
        }
        return List.of(JsonUtils.parseObject(trimmed, CountLikeUnlikeNoteMqDTO.class));
    }

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("笔记点赞聚合消费者关闭失败", e);
            }
        }
    }
}
