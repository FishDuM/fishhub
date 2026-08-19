package hk.ljx.fishhub.count.biz.consumer;

import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.enums.CollectUnCollectNoteTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountCollectUnCollectNoteMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountCollectUnCollectNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COLLECT // 主题 Topic
        )
@Slf4j
public class CountNoteCollectConsumer implements RocketMQListener<String> {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        // 完成处理后才由 RocketMQ 确认消息，避免仅入内存队列即 ACK。
        consumeMessage(List.of(body));
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记收藏数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记收藏数】聚合消息, {}", JsonUtils.toJsonString(bodys));

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
        // 最终操作的计数对象
        List<AggregationCountCollectUnCollectNoteMqDTO> countList = Lists.newArrayList();

        for (Map.Entry<Long, List<CountCollectUnCollectNoteMqDTO>> entry : groupMap.entrySet()) {
            // 笔记 ID
            Long noteId = entry.getKey();
            // 笔记发布者 ID
            Long creatorId = null;

            List<CountCollectUnCollectNoteMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountCollectUnCollectNoteMqDTO countCollectUnCollectNoteMqDTO : list) {
                // 设置笔记发布者用户 ID
                creatorId = countCollectUnCollectNoteMqDTO.getNoteCreatorId();
                // 获取操作类型
                Integer type = countCollectUnCollectNoteMqDTO.getType();

                // 根据操作类型，获取对应枚举
                CollectUnCollectNoteTypeEnum collectUnCollectNoteTypeEnum = CollectUnCollectNoteTypeEnum.valueOf(type);

                switch (collectUnCollectNoteTypeEnum) {
                    case COLLECT -> finalCount += 1; // 如果为收藏操作，收藏数 +1
                    case UN_COLLECT -> finalCount -= 1;
                }
            }
            // 将分组后统计出的最终计数，存入 countList 中
            countList.add(AggregationCountCollectUnCollectNoteMqDTO.builder()
                    .noteId(noteId)
                    .creatorId(creatorId)
                    .count(finalCount)
                    .batchId(batchId)
                            .build());
        }

        log.info("## 【笔记收藏数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 发送 MQ, 笔记收藏数据落库
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();

        // 第一阶段不修改缓存；数据库消费者提交后统一失效缓存。
        rocketMQTemplate.syncSend(MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB, message);
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
