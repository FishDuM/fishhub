package hk.ljx.fishhub.count.biz.consumer;

import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.enums.LikeUnlikeNoteTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountLikeUnlikeNoteMqDTO;
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
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_LIKE, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_LIKE // 主题 Topic
        )
@Slf4j
public class CountNoteLikeConsumer implements RocketMQListener<String> {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        // RocketMQ 仅在本次处理返回后确认消息，进程退出时不会丢失内存队列中的消息。
        consumeMessage(List.of(body));
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // 聚合批次标识：同批重投时内容不变，不同批次的相同聚合结果可区分
        String batchId = cn.hutool.crypto.digest.DigestUtil.sha256Hex(String.join("|", bodys));

        // List<String> 转 List<CountLikeUnlikeNoteMqDTO>
        List<CountLikeUnlikeNoteMqDTO> countLikeUnlikeNoteMqDTOS = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountLikeUnlikeNoteMqDTO.class)).toList();
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

        // 发送 MQ, 笔记点赞数据落库
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();

        // 第一阶段不修改缓存；数据库消费者提交后统一失效缓存。
        rocketMQTemplate.syncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB, message);
    }
}
