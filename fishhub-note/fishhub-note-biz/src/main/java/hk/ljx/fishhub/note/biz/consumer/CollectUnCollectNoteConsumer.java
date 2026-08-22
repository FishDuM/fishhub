package hk.ljx.fishhub.note.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.CollectUnCollectNoteTypeEnum;
import hk.ljx.fishhub.note.biz.enums.NoteVisibleEnum;
import hk.ljx.fishhub.note.biz.model.dto.CollectUnCollectNoteMqDTO;
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
 * 笔记收藏/取消收藏批量消费
 */
@Component
@Slf4j
public class CollectUnCollectNoteConsumer {

    private static final int CONSUME_BATCH_MAX_SIZE = 30;
    private static final String CONSUME_GROUP = "fishhub_group_" + MQConstants.TOPIC_COLLECT_OR_UN_COLLECT;

    private final TransactionalMqSender transactionalMqSender;
    private final NoteInteractionPersistenceService persistenceService;
    private final NoteDOMapper noteDOMapper;
    private final NoteInteractionCacheService noteInteractionCacheService;
    private final BatchPushConsumer batchPushConsumer;

    // 每秒 5000 令牌，批级限速兜底
    private final RateLimiter rateLimiter = RateLimiter.create(5000);

    public CollectUnCollectNoteConsumer(TransactionalMqSender transactionalMqSender,
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
                MQConstants.TOPIC_COLLECT_OR_UN_COLLECT,
                MQConstants.TAG_COLLECT + "||" + MQConstants.TAG_UN_COLLECT,
                CONSUME_BATCH_MAX_SIZE,
                0,
                // 同用户收藏/取消必须有序落库（发送端已按 userId 路由），乱序会破坏最终状态
                BatchConsumerFactory.Mode.ORDERLY,
                this::consumeBatch);
    }

    private boolean consumeBatch(List<MessageExt> msgs) {
        try {
            rateLimiter.acquire();
            List<String> bodys = msgs.stream()
                    .map(msg -> new String(msg.getBody(), StandardCharsets.UTF_8))
                    .toList();
            consumeEventBodies(bodys);
            return true;
        } catch (Exception e) {
            log.error("笔记收藏批量消费失败，整批稍后重投", e);
            return false;
        }
    }

    void consumeEventBodies(List<String> bodys) {
        List<CollectUnCollectNoteMqDTO> events = new ArrayList<>();
        for (String body : bodys) {
            CollectUnCollectNoteMqDTO event = JsonUtils.parseObject(body, CollectUnCollectNoteMqDTO.class);
            if (event == null || event.getUserId() == null || event.getNoteId() == null
                    || event.getType() == null || event.getCreateTime() == null) {
                log.error("丢弃无法恢复的收藏消息，必要字段缺失: {}", body);
                continue;
            }
            events.add(event);
        }
        if (events.isEmpty()) {
            return;
        }

        List<Long> noteIds = events.stream().map(CollectUnCollectNoteMqDTO::getNoteId).distinct().toList();
        Map<Long, NoteDO> noteById = noteDOMapper.selectInteractionInfosByNoteIds(noteIds).stream()
                .collect(Collectors.toMap(NoteDO::getId, Function.identity(), (left, right) -> left));

        List<CollectUnCollectNoteMqDTO> validEvents = new ArrayList<>();
        List<NoteCollectionDO> noteCollections = new ArrayList<>();
        for (CollectUnCollectNoteMqDTO event : events) {
            NoteDO note = noteById.get(event.getNoteId());
            boolean collect = Objects.equals(event.getType(), CollectUnCollectNoteTypeEnum.COLLECT.getCode());
            if (collect) {
                if (!isWritable(note, event.getUserId())) {
                    noteInteractionCacheService.removeCollect(event.getUserId(), event.getNoteId());
                    log.info("丢弃不可写的收藏消息，noteId={}, userId={}", event.getNoteId(), event.getUserId());
                    continue;
                }
            } else if (note == null) {
                log.info("丢弃笔记不存在的取消收藏消息，noteId={}, userId={}", event.getNoteId(), event.getUserId());
                continue;
            }
            event.setNoteCreatorId(note.getCreatorId());
            validEvents.add(event);
            noteCollections.add(NoteCollectionDO.builder()
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
        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_COUNT_NOTE_COLLECT, payload,
                txId -> persistenceService.saveNoteCollectBatch(noteCollections, CONSUME_GROUP, batchKey, txId));
    }

    private boolean isWritable(NoteDO note, Long userId) {
        return note != null && Objects.equals(note.getStatus(), 1)
                && (Objects.equals(note.getVisible(), NoteVisibleEnum.PUBLIC.getCode())
                || Objects.equals(note.getCreatorId(), userId));
    }
}
