package hk.ljx.fishhub.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountLikeUnlikeNoteMqDTO;
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
import java.util.stream.Collectors;

/**
 * 笔记点赞数消费者
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_LIKE, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_LIKE // 主题 Topic
        )
@Slf4j
public class CountNoteLikeConsumer implements RocketMQListener<String> {

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
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));
        // List<String> 转 List<CountLikeUnlikeNoteMqDTO>
        List<CountLikeUnlikeNoteMqDTO> countLikeUnlikeNoteMqDTOS = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountLikeUnlikeNoteMqDTO.class)).toList();

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
                // 省略...
            }

            // 将分组后统计出的最终计数，存入 countList 中
            countList.add(AggregationCountLikeUnlikeNoteMqDTO.builder()
                    .noteId(noteId)
                    .creatorId(creatorId)
                    .count(finalCount)
                    .build());
        }

        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 更新 Redis
        countList.forEach(item -> {
            Long creatorId = item.getCreatorId();
            Long noteId = item.getNoteId();
            // 聚合后的计数
            Integer count = item.getCount();

            // 笔记维度计数 Redis Key
            String countNoteRedisKey = RedisKeyConstants.buildCountNoteKey(noteId);
            boolean isCountNoteExisted = redisTemplate.hasKey(countNoteRedisKey);

            // 若存在才会更新
            if (isCountNoteExisted) {
                // 对目标用户 Hash 中的点赞数字段进行计数操作
                redisTemplate.opsForHash().increment(countNoteRedisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, count);
            }

            // 更新 Redis 用户维度点赞数
            String countUserRedisKey = RedisKeyConstants.buildCountUserKey(creatorId);
            boolean isCountUserExisted = redisTemplate.hasKey(countUserRedisKey);
            if (isCountUserExisted) {
                redisTemplate.opsForHash().increment(countUserRedisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, count);
            }
        });

        // 发送 MQ, 笔记点赞数据落库
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：笔记点赞数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：笔记点赞数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
