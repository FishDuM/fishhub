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
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionPersistenceService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔记收藏/取消收藏事件的批量消费（30/批，非顺序），与点赞链同款。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CollectUnCollectNoteConsumer {

    private static final int CONSUME_BATCH_MAX_SIZE = 30;
    private static final String CONSUME_GROUP = "fishhub_group_" + MQConstants.TOPIC_COLLECT_OR_UN_COLLECT;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    private final TransactionalMqSender transactionalMqSender;
    private final NoteInteractionPersistenceService persistenceService;
    private final NoteDOMapper noteDOMapper;
    private final NoteInteractionCacheService noteInteractionCacheService;

    // 每秒 5000 令牌，批级限速兜底
    private final RateLimiter rateLimiter = RateLimiter.create(5000);

    private DefaultMQPushConsumer consumer;

    @Bean
    public DefaultMQPushConsumer collectUnCollectPushConsumer() throws MQClientException {
        consumer = new DefaultMQPushConsumer(CONSUME_GROUP);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.subscribe(MQConstants.TOPIC_COLLECT_OR_UN_COLLECT,
                MQConstants.TAG_COLLECT + "||" + MQConstants.TAG_UN_COLLECT);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(CONSUME_BATCH_MAX_SIZE);
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            try {
                rateLimiter.acquire();
                List<String> bodys = msgs.stream()
                        .map(msg -> new String(msg.getBody(), StandardCharsets.UTF_8))
                        .toList();
                consumeEventBodies(bodys);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                log.error("笔记收藏批量消费失败，整批稍后重投", e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        consumer.start();
        return consumer;
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
            if (collect ? !isWritable(note, event.getUserId()) : note == null) {
                noteInteractionCacheService.evictCollectCaches(event.getUserId());
                log.info("丢弃不可处理的笔记收藏消息，noteId={}, userId={}", event.getNoteId(), event.getUserId());
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

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("笔记收藏消费者关闭失败", e);
            }
        }
    }
}
