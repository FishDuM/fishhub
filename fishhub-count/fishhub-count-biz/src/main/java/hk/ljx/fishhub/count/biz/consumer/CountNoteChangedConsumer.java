package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.note.api.NoteChangedEventMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 消费笔记变更统一事件，维护用户笔记总数：发布 +1、删除 -1，编辑不影响计数。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_NOTE_CHANGED, // Group 组
        topic = MQConstants.TOPIC_NOTE_CHANGED // 主题 Topic
        )
@Slf4j
public class CountNoteChangedConsumer implements RocketMQListener<String> {

    private static final int CHANGE_TYPE_PUBLISH = 1;
    private static final int CHANGE_TYPE_DELETE = 0;

    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private UserCountCacheVersionService userCountCacheVersionService;

    @Override
    public void onMessage(String body) {
        log.info("==> CountNoteChangedConsumer 消费了消息 {}", body);

        NoteChangedEventMqDTO event = JsonUtils.parseObject(body, NoteChangedEventMqDTO.class);

        if (Objects.isNull(event) || event.getCreatorId() == null || event.getChangeType() == null
                || event.getNoteId() == null) {
            throw new IllegalArgumentException("笔记变更计数消息缺少必要字段");
        }

        long count = switch (event.getChangeType()) {
            case CHANGE_TYPE_PUBLISH -> 1;
            case CHANGE_TYPE_DELETE -> -1;
            // 编辑不改变笔记总数
            default -> 0;
        };

        Long creatorId = event.getCreatorId();
        if (count != 0) {
            mqIdempotentExecutor.execute("count-note-publish", count + ":" + body,
                    () -> userCountDOMapper.insertOrUpdateNoteTotalByUserId(count, creatorId));
            userCountCacheVersionService.advanceVersion(creatorId);
        }
    }

}
