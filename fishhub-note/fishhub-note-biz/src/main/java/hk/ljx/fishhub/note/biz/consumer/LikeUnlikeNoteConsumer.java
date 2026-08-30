package hk.ljx.fishhub.note.biz.consumer;

import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.LikeUnlikeNoteTypeEnum;
import hk.ljx.fishhub.note.biz.enums.NoteVisibleEnum;
import hk.ljx.fishhub.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import hk.ljx.framework.mq.consumer.BatchConsumerFactory;
import hk.ljx.framework.mq.consumer.BatchPushConsumer;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔记点赞/取消点赞批量消费
 */
@Component
@Slf4j
public class LikeUnlikeNoteConsumer {

    private static final int CONSUME_BATCH_MAX_SIZE = 30;
    private static final String CONSUME_GROUP = "fishhub_group_" + MQConstants.TOPIC_LIKE_OR_UNLIKE;

    private final TransactionalMqSender transactionalMqSender;
    private final NoteInteractionPersistenceService persistenceService;
    private final NoteDOMapper noteDOMapper;
    private final NoteInteractionCacheService noteInteractionCacheService;
    private final BatchPushConsumer batchPushConsumer;

    public LikeUnlikeNoteConsumer(TransactionalMqSender transactionalMqSender,
                                  NoteInteractionPersistenceService persistenceService,
                                  NoteDOMapper noteDOMapper,
                                  NoteInteractionCacheService noteInteractionCacheService,
                                  BatchConsumerFactory batchConsumerFactory) throws MQClientException {
        this.transactionalMqSender = transactionalMqSender;
        this.persistenceService = persistenceService;
        this.noteDOMapper = noteDOMapper;
        this.noteInteractionCacheService = noteInteractionCacheService;
        this.batchPushConsumer = batchConsumerFactory == null ? null : batchConsumerFactory.create(
                CONSUME_GROUP,
                MQConstants.TOPIC_LIKE_OR_UNLIKE,
                MQConstants.TAG_LIKE + "||" + MQConstants.TAG_UNLIKE,
                CONSUME_BATCH_MAX_SIZE,
                0,
                // 同用户点赞/取消必须有序落库（发送端已按 userId 路由），乱序会破坏最终状态
                BatchConsumerFactory.Mode.ORDERLY,
                this::consumeBatch);
    }

    private boolean consumeBatch(List<MessageExt> msgs) {
        List<String> bodys = msgs.stream()
                .map(msg -> new String(msg.getBody(), StandardCharsets.UTF_8))
                .toList();

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                consumeEventBodies(bodys);
                return true;
            } catch (org.springframework.dao.ConcurrencyFailureException e) {
                if (attempt == maxRetries) {
                    log.error("笔记点赞批量消费死锁重试 {} 次仍失败，稍后由 MQ 重投", maxRetries, e);
                    return false;
                }
                long backoff = 15L + (long) (Math.random() * 35);
                log.warn("笔记点赞批量入库遭遇 MySQL 并发争锁，执行第 {} 次本地退避重试 (backoff={}ms)", attempt, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } catch (Exception e) {
                log.error("笔记点赞批量消费失败，整批稍后重投", e);
                return false;
            }
        }
        return false;
    }

    void consumeEventBodies(List<String> bodys) {
        List<LikeUnlikeNoteMqDTO> events = new ArrayList<>();
        for (String body : bodys) {
            LikeUnlikeNoteMqDTO event = JsonUtils.parseObject(body, LikeUnlikeNoteMqDTO.class);
            if (event == null || event.getUserId() == null || event.getNoteId() == null
                    || event.getType() == null || event.getCreateTime() == null) {
                log.error("丢弃无法恢复的点赞消息，必要字段缺失: {}", body);
                continue;
            }
            events.add(event);
        }
        if (events.isEmpty()) {
            return;
        }

        List<Long> noteIds = events.stream().map(LikeUnlikeNoteMqDTO::getNoteId).distinct().toList();
        Map<Long, NoteDO> noteById = noteDOMapper.selectInteractionInfosByNoteIds(noteIds).stream()
                .collect(Collectors.toMap(NoteDO::getId, Function.identity(), (left, right) -> left));

        List<LikeUnlikeNoteMqDTO> validEvents = new ArrayList<>();
        List<NoteLikeDO> noteLikes = new ArrayList<>();
        for (LikeUnlikeNoteMqDTO event : events) {
            NoteDO note = noteById.get(event.getNoteId());
            boolean like = Objects.equals(event.getType(), LikeUnlikeNoteTypeEnum.LIKE.getCode());
            if (like) {
                if (!isWritable(note, event.getUserId())) {
                    noteInteractionCacheService.removeLike(event.getUserId(), event.getNoteId());
                    log.info("丢弃不可写的点赞消息，noteId={}, userId={}", event.getNoteId(), event.getUserId());
                    continue;
                }
            } else if (note == null) {
                log.info("丢弃笔记不存在的取消点赞消息，noteId={}, userId={}", event.getNoteId(), event.getUserId());
                continue;
            }
            event.setNoteCreatorId(note.getCreatorId());
            validEvents.add(event);
            noteLikes.add(NoteLikeDO.builder()
                    .userId(event.getUserId())
                    .noteId(event.getNoteId())
                    .createTime(event.getCreateTime())
                    .status(event.getType())
                    .build());
        }
        if (validEvents.isEmpty()) {
            return;
        }

        String batchKey = DigestUtil.sha256Hex(String.join("|", bodys));
        String payload = JsonUtils.toJsonString(validEvents);

        Map<String, NoteLikeDO> deduplicateMap = new java.util.LinkedHashMap<>();
        for (NoteLikeDO item : noteLikes) {
            String key = item.getUserId() + ":" + item.getNoteId();
            NoteLikeDO existing = deduplicateMap.get(key);
            if (existing == null || item.getCreateTime().isAfter(existing.getCreateTime())) {
                deduplicateMap.put(key, item);
            }
        }
        List<NoteLikeDO> finalNoteLikes = deduplicateMap.values().stream()
                .sorted(java.util.Comparator.comparing(NoteLikeDO::getUserId, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                        .thenComparing(NoteLikeDO::getNoteId, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .toList();

        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_COUNT_NOTE_LIKE, payload,
                txId -> persistenceService.saveNoteLikeBatch(finalNoteLikes, CONSUME_GROUP, batchKey, txId));
    }

    private boolean isWritable(NoteDO note, Long userId) {
        return note != null && Objects.equals(note.getStatus(), 1)
                && (Objects.equals(note.getVisible(), NoteVisibleEnum.PUBLIC.getCode())
                || Objects.equals(note.getCreatorId(), userId));
    }
}
