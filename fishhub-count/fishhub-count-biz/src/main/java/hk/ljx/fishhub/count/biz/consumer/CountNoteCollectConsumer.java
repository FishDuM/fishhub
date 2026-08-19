package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.CollectUnCollectNoteTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountCollectUnCollectNoteMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountCollectUnCollectNoteMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COLLECT // 主题 Topic
        )
@Slf4j
@RequiredArgsConstructor
public class CountNoteCollectConsumer implements RocketMQListener<String> {

    private final NoteCountDOMapper noteCountDOMapper;
    private final UserCountDOMapper userCountDOMapper;
    private final MqIdempotentExecutor mqIdempotentExecutor;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserCountCacheVersionService userCountCacheVersionService;

    @Override
    public void onMessage(String body) {
        // 完成处理后才由 RocketMQ 确认消息，避免仅入内存队列即 ACK。
        consumeMessage(List.of(body));
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记收藏数】聚合落库消息, size: {}", bodys.size());
        log.info("==> 【笔记收藏数】聚合落库消息, {}", JsonUtils.toJsonString(bodys));

        // 聚合批次标识：同批重投时内容不变，不同批次的相同聚合结果可区分（对输入消息排序保证确定性）
        String batchId = cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                bodys.stream().sorted().collect(Collectors.joining("|")));

        // 兼容批量数组与旧版单条消息
        List<CountCollectUnCollectNoteMqDTO> countCollectUnCollectNoteMqDTOS = bodys.stream()
                .flatMap(body -> parseEvents(body).stream())
                .toList();
        if (countCollectUnCollectNoteMqDTOS.stream().anyMatch(item -> item == null || item.getNoteId() == null
                || item.getNoteCreatorId() == null
                || CollectUnCollectNoteTypeEnum.valueOf(item.getType()) == null)) {
            throw new IllegalArgumentException("笔记收藏计数消息缺少必要字段或操作类型无效");
        }

        // 按笔记 ID 进行分组
        Map<Long, List<CountCollectUnCollectNoteMqDTO>> groupMap = countCollectUnCollectNoteMqDTOS.stream()
                .collect(Collectors.groupingBy(CountCollectUnCollectNoteMqDTO::getNoteId));

        // 按组汇总数据，统计出最终的计数
        List<AggregationCountCollectUnCollectNoteMqDTO> countList = new ArrayList<>();

        for (Map.Entry<Long, List<CountCollectUnCollectNoteMqDTO>> entry : groupMap.entrySet()) {
            Long noteId = entry.getKey();
            Long creatorId = null;

            List<CountCollectUnCollectNoteMqDTO> list = entry.getValue();
            int finalCount = 0;
            for (CountCollectUnCollectNoteMqDTO countCollectUnCollectNoteMqDTO : list) {
                creatorId = countCollectUnCollectNoteMqDTO.getNoteCreatorId();
                Integer type = countCollectUnCollectNoteMqDTO.getType();
                CollectUnCollectNoteTypeEnum collectUnCollectNoteTypeEnum = CollectUnCollectNoteTypeEnum.valueOf(type);

                switch (collectUnCollectNoteTypeEnum) {
                    case COLLECT -> finalCount += 1;
                    case UN_COLLECT -> finalCount -= 1;
                }
            }
            countList.add(AggregationCountCollectUnCollectNoteMqDTO.builder()
                    .noteId(noteId)
                    .creatorId(creatorId)
                    .count(finalCount)
                    .batchId(batchId)
                    .build());
        }

        log.info("## 【笔记收藏数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 直接落库与缓存失效，消除二次 MQ 转发
        boolean applied = mqIdempotentExecutor.execute("count-note-collect", batchId, () -> {
            // 1. 在内存中按 noteId 聚合增量，并严格按 noteId 升序更新，消除 MySQL 行锁交叉死锁
            countList.stream()
                    .collect(Collectors.groupingBy(AggregationCountCollectUnCollectNoteMqDTO::getNoteId,
                            Collectors.summingInt(AggregationCountCollectUnCollectNoteMqDTO::getCount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> noteCountDOMapper.insertOrUpdateCollectTotalByNoteId(entry.getValue(), entry.getKey()));

            // 2. 在内存中按 creatorId 聚合增量，并严格按 creatorId 升序更新，消除 MySQL 行锁交叉死锁
            countList.stream()
                    .collect(Collectors.groupingBy(AggregationCountCollectUnCollectNoteMqDTO::getCreatorId,
                            Collectors.summingInt(AggregationCountCollectUnCollectNoteMqDTO::getCount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> userCountDOMapper.insertOrUpdateCollectTotalByUserId(entry.getValue(), entry.getKey()));
        });

        stringRedisTemplate.delete(countList.stream()
                .map(item -> RedisKeyConstants.buildCountNoteKey(item.getNoteId()))
                .distinct()
                .toList());
        countList.stream().map(AggregationCountCollectUnCollectNoteMqDTO::getCreatorId).distinct()
                .forEach(userCountCacheVersionService::advanceVersion);
        if (!applied) {
            log.info("笔记收藏计数消息已处理，忽略重复投递, batchId={}", batchId);
        }
    }

    private List<CountCollectUnCollectNoteMqDTO> parseEvents(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            try {
                return JsonUtils.parseList(trimmed, CountCollectUnCollectNoteMqDTO.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("笔记收藏计数消息格式错误", e);
            }
        }
        return List.of(JsonUtils.parseObject(trimmed, CountCollectUnCollectNoteMqDTO.class));
    }
}
