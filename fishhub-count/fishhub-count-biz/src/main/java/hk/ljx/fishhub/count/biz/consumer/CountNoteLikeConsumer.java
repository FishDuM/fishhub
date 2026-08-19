package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.LikeUnlikeNoteTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountLikeUnlikeNoteMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 笔记点赞/取消点赞事件的批量聚合与落库消费者。
 *
 * <p>手动 {@link DefaultMQPushConsumer} 接收整批消息，聚合成每 noteId/creatorId 净增量后
 * 严格按 ID 升序直接更新 MySQL 并失效 Redis 计数缓存，无需二次 MQ 转发。
 */
@Component
@Slf4j
public class CountNoteLikeConsumer {

    /** 每批最多拉取的消息数 */
    private static final int CONSUME_BATCH_MAX_SIZE = 30;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserCountCacheVersionService userCountCacheVersionService;

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
                log.error("笔记点赞计数聚合落库消费失败，整批稍后重投", e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        consumer.start();
        return consumer;
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合落库消息, size: {}", bodys.size());

        // 聚合批次标识：同批重投时内容不变（对输入消息排序保证确定性）
        String batchId = cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                bodys.stream().sorted().collect(Collectors.joining("|")));

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
        List<AggregationCountLikeUnlikeNoteMqDTO> countList = new ArrayList<>();

        for (Map.Entry<Long, List<CountLikeUnlikeNoteMqDTO>> entry : groupMap.entrySet()) {
            Long noteId = entry.getKey();
            Long creatorId = null;
            List<CountLikeUnlikeNoteMqDTO> list = entry.getValue();
            int finalCount = 0;
            for (CountLikeUnlikeNoteMqDTO countLikeUnlikeNoteMqDTO : list) {
                creatorId = countLikeUnlikeNoteMqDTO.getNoteCreatorId();
                Integer type = countLikeUnlikeNoteMqDTO.getType();
                LikeUnlikeNoteTypeEnum likeUnlikeNoteTypeEnum = LikeUnlikeNoteTypeEnum.valueOf(type);

                switch (likeUnlikeNoteTypeEnum) {
                    case LIKE -> finalCount += 1;
                    case UNLIKE -> finalCount -= 1;
                }
            }
            countList.add(AggregationCountLikeUnlikeNoteMqDTO.builder()
                    .noteId(noteId)
                    .creatorId(creatorId)
                    .count(finalCount)
                    .batchId(batchId)
                    .build());
        }

        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 直接落库与缓存失效，消除二次 MQ 转发
        boolean applied = mqIdempotentExecutor.execute("count-note-like", batchId, () -> {
            // 1. 在内存中按 noteId 聚合增量，并严格按 noteId 升序更新，消除 MySQL 行锁交叉死锁
            countList.stream()
                    .collect(Collectors.groupingBy(AggregationCountLikeUnlikeNoteMqDTO::getNoteId,
                            Collectors.summingInt(AggregationCountLikeUnlikeNoteMqDTO::getCount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> noteCountDOMapper.insertOrUpdateLikeTotalByNoteId(entry.getValue(), entry.getKey()));

            // 2. 在内存中按 creatorId 聚合增量，并严格按 creatorId 升序更新，消除 MySQL 行锁交叉死锁
            countList.stream()
                    .collect(Collectors.groupingBy(AggregationCountLikeUnlikeNoteMqDTO::getCreatorId,
                            Collectors.summingInt(AggregationCountLikeUnlikeNoteMqDTO::getCount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> userCountDOMapper.insertOrUpdateLikeTotalByUserId(entry.getValue(), entry.getKey()));
        });

        stringRedisTemplate.delete(countList.stream()
                .map(item -> RedisKeyConstants.buildCountNoteKey(item.getNoteId()))
                .distinct()
                .toList());
        countList.stream().map(AggregationCountLikeUnlikeNoteMqDTO::getCreatorId).distinct()
                .forEach(userCountCacheVersionService::advanceVersion);
        if (!applied) {
            log.info("笔记点赞计数消息已处理，忽略重复投递, batchId={}", batchId);
        }
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
