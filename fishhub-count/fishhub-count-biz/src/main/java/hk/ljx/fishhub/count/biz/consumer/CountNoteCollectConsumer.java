package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.consumer.aggregation.AbstractNoteCountAggregationConsumer;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.CollectUnCollectNoteTypeEnum;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 笔记收藏/取消收藏计数消费者
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT,
        topic = MQConstants.TOPIC_COUNT_NOTE_COLLECT)
@Slf4j
public class CountNoteCollectConsumer extends AbstractNoteCountAggregationConsumer implements RocketMQListener<String> {

    public CountNoteCollectConsumer(NoteCountDOMapper noteCountDOMapper,
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
        return "count-note-collect";
    }

    @Override
    protected String bizLabel() {
        return "笔记收藏数";
    }

    @Override
    protected boolean isValidType(Integer type) {
        return CollectUnCollectNoteTypeEnum.valueOf(type) != null;
    }

    @Override
    protected int deltaOf(Integer type) {
        return switch (CollectUnCollectNoteTypeEnum.valueOf(type)) {
            case COLLECT -> 1;
            case UN_COLLECT -> -1;
        };
    }

    @Override
    protected void updateNoteCount(Long noteId, int delta) {
        noteCountDOMapper.insertOrUpdateCollectTotalByNoteId(delta, noteId);
    }

    @Override
    protected void updateUserCount(Long userId, int delta) {
        userCountDOMapper.insertOrUpdateCollectTotalByUserId(delta, userId);
    }
}
