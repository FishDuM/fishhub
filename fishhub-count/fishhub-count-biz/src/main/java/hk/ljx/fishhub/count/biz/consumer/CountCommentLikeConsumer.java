package hk.ljx.fishhub.count.biz.consumer;

import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountLikeUnlikeCommentMqDTO;
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
@RocketMQMessageListener(consumerGroup = "fishhub_group_count_" + MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE,
        topic = MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE
        )
@Slf4j
public class CountCommentLikeConsumer implements RocketMQListener<String> {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        // 完成处理后才由 RocketMQ 确认消息，避免仅入内存队列即 ACK。
        consumeMessage(List.of(body));
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【评论点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【评论点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // 聚合批次标识：同批重投时内容不变，不同批次的相同聚合结果可区分
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

        // 发送 MQ, 评论点赞数据落库
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();

        // 第一阶段不修改缓存；数据库消费者提交后统一失效缓存。
        rocketMQTemplate.syncSend(MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB, message);
    }
}
