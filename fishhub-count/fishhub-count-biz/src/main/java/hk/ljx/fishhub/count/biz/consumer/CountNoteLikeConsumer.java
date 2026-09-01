package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.consumer.aggregation.AbstractNoteCountAggregationConsumer;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.LikeUnlikeNoteTypeEnum;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 笔记点赞/取消点赞计数消费者
 */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_LIKE,
        topic = MQConstants.TOPIC_COUNT_NOTE_LIKE,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class CountNoteLikeConsumer extends AbstractNoteCountAggregationConsumer implements RocketMQListener<String> {

    public CountNoteLikeConsumer(NoteCountDOMapper noteCountDOMapper,
                                 UserCountDOMapper userCountDOMapper,
                                 MqIdempotentExecutor mqIdempotentExecutor,
                                 StringRedisTemplate stringRedisTemplate) {
        super(noteCountDOMapper, userCountDOMapper, mqIdempotentExecutor, stringRedisTemplate);
    }

    @Override
    public void onMessage(String body) {
        consumeBatches(List.of(body));
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
