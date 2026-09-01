package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.note.api.NoteChangedEventMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 消费笔记变更统一事件，维护用户笔记总数与计数缓存清理：
 * 1. 笔记发布 +1、删除 -1；
 * 2. 笔记删除或设为私密时，清理对应笔记的 Redis 计数缓存。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_NOTE_CHANGED,
        topic = MQConstants.TOPIC_NOTE_CHANGED
        )
@Slf4j
@RequiredArgsConstructor
public class CountNoteChangedConsumer implements RocketMQListener<String> {

    private static final int CHANGE_TYPE_PUBLISH = 1;
    private static final int CHANGE_TYPE_DELETE = 0;

    private final UserCountDOMapper userCountDOMapper;
    private final MqIdempotentExecutor mqIdempotentExecutor;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(String body) {
        log.info("==> CountNoteChangedConsumer 消费了消息 {}", body);

        NoteChangedEventMqDTO event = JsonUtils.parseObject(body, NoteChangedEventMqDTO.class);

        if (Objects.isNull(event) || event.getCreatorId() == null || event.getChangeType() == null
                || event.getNoteId() == null) {
            throw new IllegalArgumentException("笔记变更计数消息缺少必要字段");
        }

        Long noteId = event.getNoteId();

        // 笔记删除或转为私密状态时 (visible=1 为 PRIVATE)，物理清理 Redis 计数缓存
        if (Objects.equals(event.getChangeType(), CHANGE_TYPE_DELETE) || Objects.equals(event.getVisible(), 1)) {
            stringRedisTemplate.delete(CountKeyConstants.buildCountNoteKey(noteId));
            CountClient.invalidate(noteId);
        }

        long count = switch (event.getChangeType()) {
            case CHANGE_TYPE_PUBLISH -> 1;
            case CHANGE_TYPE_DELETE -> -1;
            default -> 0;
        };

        Long creatorId = event.getCreatorId();
        if (count != 0) {
            mqIdempotentExecutor.execute("count-note-publish", count + ":" + body,
                    () -> userCountDOMapper.insertOrUpdateNoteTotalByUserId(count, creatorId));
            stringRedisTemplate.delete(CountKeyConstants.buildCountUserKey(creatorId));
        }
    }

}
