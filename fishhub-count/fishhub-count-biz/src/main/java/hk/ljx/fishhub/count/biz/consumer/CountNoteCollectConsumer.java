package hk.ljx.fishhub.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.enums.CollectUnCollectNoteTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountCollectUnCollectNoteMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountCollectUnCollectNoteMqDTO;
import hk.ljx.framework.common.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COLLECT // 主题 Topic
        )
@Slf4j
public class CountNoteCollectConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000)
            .batchSize(1000)
            .linger(Duration.ofSeconds(1))
            .setConsumerEx(this::consumeMessage)
            .build();

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记收藏数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记收藏数】聚合消息, {}", JsonUtils.toJsonString(bodys));
        List<CountCollectUnCollectNoteMqDTO> countCollectUnCollectNoteMqDTOS = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountCollectUnCollectNoteMqDTO.class)).toList();

        // 按笔记 ID 进行分组
        Map<Long, List<CountCollectUnCollectNoteMqDTO>> groupMap = countCollectUnCollectNoteMqDTOS.stream()
                .collect(Collectors.groupingBy(CountCollectUnCollectNoteMqDTO::getNoteId));

        List<AggregationCountCollectUnCollectNoteMqDTO> countList = Lists.newArrayList();

        for (Map.Entry<Long, List<CountCollectUnCollectNoteMqDTO>> entry : groupMap.entrySet()) {
            Long creatorId = null;
            List<CountCollectUnCollectNoteMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountCollectUnCollectNoteMqDTO countCollectUnCollectNoteMqDTO : list) {
                creatorId = countCollectUnCollectNoteMqDTO.getNoteCreatorId();
                // 获取操作类型
                Integer type = countCollectUnCollectNoteMqDTO.getType();

                // 根据操作类型，获取对应枚举
                CollectUnCollectNoteTypeEnum collectUnCollectNoteTypeEnum = CollectUnCollectNoteTypeEnum.valueOf(type);

                // 若枚举为空，跳到下一次循环
                if (Objects.isNull(collectUnCollectNoteTypeEnum)) continue;

                switch (collectUnCollectNoteTypeEnum) {
                    case COLLECT -> finalCount += 1; // 如果为收藏操作，收藏数 +1
                    case UN_COLLECT -> finalCount -= 1; // 如果为取消收藏操作，收藏数 -1
                }
            }
            countList.add(AggregationCountCollectUnCollectNoteMqDTO.builder()
                    .noteId(entry.getKey())
                    .creatorId(creatorId)
                    .count(finalCount)
                    .build());
        }
        log.info("## 【笔记收藏数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 更新 Redis
        countList.forEach(item -> {
            String noteRedisKey = RedisKeyConstants.buildCountNoteKey(item.getNoteId());
            boolean isNoteCountExisted = redisTemplate.hasKey(noteRedisKey);

            // 若存在才会更新
            if (isNoteCountExisted) {
                redisTemplate.opsForHash().increment(noteRedisKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, item.getCount());
            }

            String userRedisKey = RedisKeyConstants.buildCountUserKey(item.getCreatorId());
            boolean isUserCountExisted = redisTemplate.hasKey(userRedisKey);
            if (isUserCountExisted) {
                redisTemplate.opsForHash().increment(userRedisKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, item.getCount());
            }
        });

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();

        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：笔记收藏数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：笔记收藏数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
