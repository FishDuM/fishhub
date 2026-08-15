package hk.ljx.fishhub.note.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.NoteVisibleEnum;
import hk.ljx.fishhub.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import hk.ljx.fishhub.note.biz.retry.ReliableMqOutbox;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionPersistenceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, // Group 组
        topic = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, // 消费的主题 Topic
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
@Slf4j
public class CollectUnCollectNoteConsumer implements RocketMQListener<Message> {

    @Resource
    private ReliableMqOutbox reliableMqOutbox;
    @Resource
    private NoteInteractionPersistenceService persistenceService;
    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private NoteInteractionCacheService noteInteractionCacheService;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 幂等性: 通过联合唯一索引保证

        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 标签
        String tags = message.getTags();

        log.info("==> CollectUnCollectNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        // 根据 MQ 标签，判断操作类型
        if (Objects.equals(tags, MQConstants.TAG_COLLECT)) { // 收藏笔记
            handleCollectNoteTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UN_COLLECT)) { // 取消收藏笔记
            handleUnCollectNoteTagMessage(bodyJsonStr);
        }
    }

    /**
     * 笔记收藏
     * @param bodyJsonStr
     */
    private void handleCollectNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);

        if (Objects.isNull(collectUnCollectNoteMqDTO)) return;

        // 用户ID
        Long userId = collectUnCollectNoteMqDTO.getUserId();
        // 收藏的笔记ID
        Long noteId = collectUnCollectNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = collectUnCollectNoteMqDTO.getType();
        // 收藏时间
        LocalDateTime createTime = collectUnCollectNoteMqDTO.getCreateTime();

        if (userId == null || noteId == null || type == null || createTime == null) {
            log.error("丢弃无法恢复的收藏消息，必要字段缺失: {}", bodyJsonStr);
            return;
        }
        NoteDO note = noteDOMapper.selectInteractionInfoByNoteId(noteId);
        if (!isWritable(note, userId)) {
            noteInteractionCacheService.evictCollectCaches(userId);
            log.info("丢弃不可写笔记的收藏消息，noteId={}, userId={}", noteId, userId);
            return;
        }
        collectUnCollectNoteMqDTO.setNoteCreatorId(note.getCreatorId());

        // 构建 DO 对象
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        String resolvedBody = JsonUtils.toJsonString(collectUnCollectNoteMqDTO);
        if (persistenceService.saveCollect(noteCollectionDO, resolvedBody)) {
            reliableMqOutbox.sendNow(MQConstants.TOPIC_COUNT_NOTE_COLLECT, resolvedBody);
        }
    }

    /**
     * 笔记取消收藏
     * @param bodyJsonStr
     */
    private void handleUnCollectNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);

        if (Objects.isNull(unCollectNoteMqDTO)) return;

        // 用户ID
        Long userId = unCollectNoteMqDTO.getUserId();
        // 收藏的笔记ID
        Long noteId = unCollectNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = unCollectNoteMqDTO.getType();
        // 收藏时间
        LocalDateTime createTime = unCollectNoteMqDTO.getCreateTime();

        if (userId == null || noteId == null || type == null || createTime == null) {
            log.error("丢弃无法恢复的取消收藏消息，必要字段缺失: {}", bodyJsonStr);
            return;
        }
        NoteDO note = noteDOMapper.selectInteractionInfoByNoteId(noteId);
        if (note == null) {
            noteInteractionCacheService.evictCollectCaches(userId);
            log.info("丢弃不存在笔记的取消收藏消息，noteId={}, userId={}", noteId, userId);
            return;
        }
        unCollectNoteMqDTO.setNoteCreatorId(note.getCreatorId());

        // 构建 DO 对象
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        String resolvedBody = JsonUtils.toJsonString(unCollectNoteMqDTO);
        if (persistenceService.saveUncollect(noteCollectionDO, resolvedBody)) {
            reliableMqOutbox.sendNow(MQConstants.TOPIC_COUNT_NOTE_COLLECT, resolvedBody);
        }
    }

    private boolean isWritable(NoteDO note, Long userId) {
        return note != null && Objects.equals(note.getStatus(), 1)
                && (Objects.equals(note.getVisible(), NoteVisibleEnum.PUBLIC.getCode())
                || Objects.equals(note.getCreatorId(), userId));
    }

}
