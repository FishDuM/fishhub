package hk.ljx.fishhub.note.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.NoteVisibleEnum;
import hk.ljx.fishhub.note.biz.model.dto.LikeUnlikeNoteMqDTO;
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
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_LIKE_OR_UNLIKE, // Group 组
        topic = MQConstants.TOPIC_LIKE_OR_UNLIKE, // 消费的主题 Topic
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
@Slf4j
public class LikeUnlikeNoteConsumer implements RocketMQListener<Message> {

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

        log.info("==> LikeUnlikeNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        // 根据 MQ 标签，判断操作类型
        if (Objects.equals(tags, MQConstants.TAG_LIKE)) { // 点赞笔记
            handleLikeNoteTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UNLIKE)) { // 取消点赞笔记
            handleUnlikeNoteTagMessage(bodyJsonStr);
        }
    }

    /**
     * 笔记点赞
     * @param bodyJsonStr
     */
    private void handleLikeNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        LikeUnlikeNoteMqDTO likeNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, LikeUnlikeNoteMqDTO.class);

        if (Objects.isNull(likeNoteMqDTO)) return;

        // 用户ID
        Long userId = likeNoteMqDTO.getUserId();
        // 点赞的笔记ID
        Long noteId = likeNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = likeNoteMqDTO.getType();
        // 点赞时间
        LocalDateTime createTime = likeNoteMqDTO.getCreateTime();

        if (userId == null || noteId == null || type == null || createTime == null) {
            log.error("丢弃无法恢复的点赞消息，必要字段缺失: {}", bodyJsonStr);
            return;
        }

        NoteDO note = noteDOMapper.selectInteractionInfoByNoteId(noteId);
        if (!isWritable(note, userId)) {
            // 接口已乐观更新 Redis；拒绝落库后清空该用户缓存，后续刷新会从 MySQL 恢复真实状态。
            noteInteractionCacheService.evictLikeCaches(userId);
            log.info("丢弃不可写笔记的点赞消息，noteId={}, userId={}", noteId, userId);
            return;
        }
        likeNoteMqDTO.setNoteCreatorId(note.getCreatorId());

        // 构建 DO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        String resolvedBody = JsonUtils.toJsonString(likeNoteMqDTO);
        if (persistenceService.saveLike(noteLikeDO, resolvedBody)) {
            reliableMqOutbox.sendNow(MQConstants.TOPIC_COUNT_NOTE_LIKE, resolvedBody);
        }
    }

    /**
     * 笔记取消点赞
     * @param bodyJsonStr
     */
    private void handleUnlikeNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        LikeUnlikeNoteMqDTO unlikeNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, LikeUnlikeNoteMqDTO.class);

        if (Objects.isNull(unlikeNoteMqDTO)) return;

        // 用户ID
        Long userId = unlikeNoteMqDTO.getUserId();
        // 点赞的笔记ID
        Long noteId = unlikeNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = unlikeNoteMqDTO.getType();
        // 点赞时间
        LocalDateTime createTime = unlikeNoteMqDTO.getCreateTime();

        if (userId == null || noteId == null || type == null || createTime == null) {
            log.error("丢弃无法恢复的取消点赞消息，必要字段缺失: {}", bodyJsonStr);
            return;
        }

        // 取消操作只会清理关系，即使笔记已转私密或删除也应允许；逻辑删除记录仍可提供计数所需的作者 ID。
        NoteDO note = noteDOMapper.selectInteractionInfoByNoteId(noteId);
        if (note == null) {
            noteInteractionCacheService.evictLikeCaches(userId);
            log.info("丢弃不存在笔记的取消点赞消息，noteId={}, userId={}", noteId, userId);
            return;
        }
        unlikeNoteMqDTO.setNoteCreatorId(note.getCreatorId());

        // 构建 DO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        String resolvedBody = JsonUtils.toJsonString(unlikeNoteMqDTO);
        if (persistenceService.saveUnlike(noteLikeDO, resolvedBody)) {
            reliableMqOutbox.sendNow(MQConstants.TOPIC_COUNT_NOTE_LIKE, resolvedBody);
        }
    }

    private boolean isWritable(NoteDO note, Long userId) {
        return note != null && Objects.equals(note.getStatus(), 1)
                && (Objects.equals(note.getVisible(), NoteVisibleEnum.PUBLIC.getCode())
                || Objects.equals(note.getCreatorId(), userId));
    }

}
