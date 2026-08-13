package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.NoteContentTaskTypeEnum;
import hk.ljx.fishhub.note.biz.model.dto.NoteContentTaskMqDTO;
import hk.ljx.fishhub.note.biz.rpc.KeyValueRpcService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_SYNC_NOTE_CONTENT,
        topic = MQConstants.TOPIC_SYNC_NOTE_CONTENT)
public class SyncNoteContentConsumer implements RocketMQListener<String> {

    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private NoteDOMapper noteDOMapper;

    @Override
    public void onMessage(String body) {
        NoteContentTaskMqDTO task = JsonUtils.parseObject(body, NoteContentTaskMqDTO.class);
        if (task == null || task.getNoteId() == null || StringUtils.isBlank(task.getContentUuid()) || task.getType() == null) {
            throw new IllegalArgumentException("笔记正文同步消息缺少必要字段");
        }
        NoteContentTaskTypeEnum type;
        try {
            type = NoteContentTaskTypeEnum.valueOf(task.getType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("笔记正文同步消息类型不合法", e);
        }
        boolean succeeded = switch (type) {
            case UPSERT -> syncCurrentContent(task);
            case DELETE -> keyValueRpcService.deleteNoteContent(task.getContentUuid());
        };
        if (!succeeded) {
            throw new IllegalStateException("笔记正文同步到 KV 失败");
        }
    }

    private boolean syncCurrentContent(NoteContentTaskMqDTO task) {
        NoteDO current = noteDOMapper.selectByPrimaryKey(task.getNoteId());
        // 笔记被删除或正文已更新时，旧任务不再具有写入资格，直接确认即可。
        if (!matchesCurrentContent(current, task)) {
            return keyValueRpcService.deleteNoteContent(task.getContentUuid());
        }
        boolean saved = StringUtils.isNotBlank(task.getContent())
                && keyValueRpcService.saveNoteContent(task.getContentUuid(), task.getContent());
        if (!saved) {
            return false;
        }
        // 删除或替换可能发生在写前校验和 KV 写入之间；写后再核验一次并清理刚写入的旧正文。
        return matchesCurrentContent(noteDOMapper.selectByPrimaryKey(task.getNoteId()), task)
                || keyValueRpcService.deleteNoteContent(task.getContentUuid());
    }

    private boolean matchesCurrentContent(NoteDO note, NoteContentTaskMqDTO task) {
        return note != null && StringUtils.equals(note.getContentUuid(), task.getContentUuid());
    }
}
