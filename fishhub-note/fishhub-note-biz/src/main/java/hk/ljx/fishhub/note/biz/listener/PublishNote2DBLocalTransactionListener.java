package hk.ljx.fishhub.note.biz.listener;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.convert.NoteConvert;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.NoteOperateEnum;
import hk.ljx.fishhub.note.biz.model.dto.NoteOperateMqDTO;
import hk.ljx.fishhub.note.biz.model.dto.PublishNoteDTO;
import hk.ljx.fishhub.note.biz.rpc.KeyValueRpcService;
import hk.ljx.fishhub.note.biz.retry.ReliableMqOutbox;
import hk.ljx.fishhub.note.biz.service.NotePersistenceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;


@RocketMQTransactionListener
@Slf4j
public class PublishNote2DBLocalTransactionListener implements RocketMQLocalTransactionListener {

    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private ReliableMqOutbox reliableMqOutbox;
    @Resource
    private NotePersistenceService notePersistenceService;
    @Resource
    private KeyValueRpcService keyValueRpcService;

    /**
     * 执行本地事务
     * @param msg
     * @param arg
     * @return
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        // 1. 解析消息内容
        String payload = new String((byte[]) msg.getPayload());
        log.info("## 事务消息: 开始执行本地事务：{}", payload);

        // 消息体 Json 转 DTO
        PublishNoteDTO publishNoteDTO = JsonUtils.parseObject(payload, PublishNoteDTO.class);

        Long noteId = publishNoteDTO.getId();
        Long creatorId = publishNoteDTO.getCreatorId();

        boolean contentSaveAttempted = false;

        // 2. 保存正文并写入笔记元数据。KV 不参与 MySQL 事务，因此元数据写入失败时需要补偿删除正文。
        try {
            if (StringUtils.isNotBlank(publishNoteDTO.getContent())) {
                contentSaveAttempted = true;
                boolean contentSaved = keyValueRpcService.saveNoteContent(
                        publishNoteDTO.getContentUuid(), publishNoteDTO.getContent());
                if (!contentSaved) {
                    log.error("## 笔记正文存储失败, noteId={}", noteId);
                    compensateContentDelete(noteId, publishNoteDTO.getContentUuid());
                    return RocketMQLocalTransactionState.ROLLBACK;
                }
            }

            // DTO 转 DO
            NoteDO noteDO = NoteConvert.INSTANCE.convertDTO2DO(publishNoteDTO);

            NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                    .creatorId(creatorId)
                    .noteId(noteId)
                    .type(NoteOperateEnum.PUBLISH.getCode())
                    .build();
            String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_PUBLISH;
            String eventBody = JsonUtils.toJsonString(noteOperateMqDTO);

            // 笔记元数据与发布事件在同一个 MySQL 事务中提交。
            notePersistenceService.savePublishedNote(noteDO, destination, eventBody);

            // 事务已经提交；即时投递失败时由 outbox 定时补发。
            reliableMqOutbox.sendNow(MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE, eventBody);
            reliableMqOutbox.sendNow(destination, eventBody);
        } catch (Exception e) {
            log.error("## 笔记元数据存储失败: ", e);
            if (contentSaveAttempted) {
                compensateContentDelete(noteId, publishNoteDTO.getContentUuid());
            }
            return RocketMQLocalTransactionState.ROLLBACK; // 回滚事务消息
        }

        // 3. 提交事务状态，“half 消息” 转换为正式消息
        return RocketMQLocalTransactionState.COMMIT;
    }

    private void compensateContentDelete(Long noteId, String contentUuid) {
        if (!keyValueRpcService.deleteNoteContent(contentUuid)) {
            log.error("## 笔记正文回滚补偿失败, noteId={}, contentUuid={}", noteId, contentUuid);
        }
    }

    /**
     * 事务状态回查（由 Broker 主动调用）
     * @param msg
     * @return
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 1. 解析消息内容
        String payload = new String((byte[]) msg.getPayload());
        log.info("## 事务消息: 开始事务回查：{}", payload);

        // 消息体 Json 转 DTO
        PublishNoteDTO publishNoteDTO = JsonUtils.parseObject(payload, PublishNoteDTO.class);

        Long noteId = publishNoteDTO.getId();

        // 2. 检查本地事务状态（若记录存在，说明本地事务执行成功了；否则执行失败）
        int count = noteDOMapper.selectCountByNoteId(noteId);

        // 3. 返回最终状态
        return count == 1 ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }

}
