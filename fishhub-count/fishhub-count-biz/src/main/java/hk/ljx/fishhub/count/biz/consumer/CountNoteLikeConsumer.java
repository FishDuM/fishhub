package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.consumer.aggregation.AbstractNoteCountAggregationConsumer;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.LikeUnlikeNoteTypeEnum;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 笔记点赞/取消点赞计数微批消费者
 */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_LIKE,
        topic = MQConstants.TOPIC_COUNT_NOTE_LIKE,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class CountNoteLikeConsumer extends AbstractNoteCountAggregationConsumer implements RocketMQListener<List<MessageExt>> {

    /** 每批最多拉取的消息数 */
    private static final int CONSUME_BATCH_MAX_SIZE = 30;

    public CountNoteLikeConsumer(NoteCountDOMapper noteCountDOMapper,
                                 UserCountDOMapper userCountDOMapper,
                                 MqIdempotentExecutor mqIdempotentExecutor,
                                 StringRedisTemplate stringRedisTemplate,
                                 UserCountCacheVersionService userCountCacheVersionService) {
        super(noteCountDOMapper, userCountDOMapper, mqIdempotentExecutor, stringRedisTemplate, userCountCacheVersionService);
    }

    @Override
    public void onMessage(List<MessageExt> msgs) {
        if (CollUtil.isEmpty(msgs)) {
            return;
        }
        boolean success = consumeBatch(msgs);
        if (!success) {
            throw new RuntimeException("笔记点赞计数微批消费失败，触发 RocketMQ 重试");
        }
    }

    private boolean consumeBatch(List<org.apache.rocketmq.common.message.MessageExt> msgs) {
        try {
            List<String> bodys = msgs.stream()
                    .map(msg -> new String(msg.getBody(), StandardCharsets.UTF_8))
                    .toList();
            consumeBatches(bodys);
            return true;
        } catch (Exception e) {
            log.error("笔记点赞计数聚合落库消费失败，整批稍后重投", e);
            return false;
        }
    }

    @Override
    protected String idempotentGroup() {
        return "count-note-like";
    }

    @Override
    protected String bizLabel() {
        return "笔记点赞数";
    }

    @Override
    protected boolean isValidType(Integer type) {
        return LikeUnlikeNoteTypeEnum.valueOf(type) != null;
    }

    @Override
    protected int deltaOf(Integer type) {
        return switch (LikeUnlikeNoteTypeEnum.valueOf(type)) {
            case LIKE -> 1;
            case UNLIKE -> -1;
        };
    }

    @Override
    protected void updateNoteCount(Long noteId, int delta) {
        noteCountDOMapper.insertOrUpdateLikeTotalByNoteId(delta, noteId);
    }

    @Override
    protected void updateUserCount(Long userId, int delta) {
        userCountDOMapper.insertOrUpdateLikeTotalByUserId(delta, userId);
    }
}
