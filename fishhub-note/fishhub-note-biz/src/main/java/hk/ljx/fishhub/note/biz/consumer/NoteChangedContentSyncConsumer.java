package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.NoteContentTaskTypeEnum;
import hk.ljx.fishhub.note.biz.model.dto.NoteChangedEventMqDTO;
import hk.ljx.fishhub.note.biz.model.dto.NoteContentTaskMqDTO;
import hk.ljx.fishhub.note.biz.rpc.KeyValueRpcService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 消费笔记变更统一事件中的正文任务，幂等同步到 KV。
 * 写前校验 contentUuid 归属 + 写后复核，天然幂等于消息重投递。
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_NOTE_CHANGED + "_content_sync",
        topic = MQConstants.TOPIC_NOTE_CHANGED)
public class NoteChangedContentSyncConsumer implements RocketMQListener<String> {

    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private NoteDOMapper noteDOMapper;

    @Override
    public void onMessage(String body) {
        NoteChangedEventMqDTO event = JsonUtils.parseObject(body, NoteChangedEventMqDTO.class);
        if (event == null || event.getNoteId() == null || event.getContentTasks() == null
                || event.getContentTasks().isEmpty()) {
            throw new IllegalArgumentException("笔记变更消息缺少必要字段");
        }
        for (NoteContentTaskMqDTO task : event.getContentTasks()) {
            if (task == null || task.getNoteId() == null || StringUtils.isBlank(task.getContentUuid())
                    || task.getType() == null) {
                throw new IllegalArgumentException("笔记正文同步任务缺少必要字段");
            }
            try {
                NoteContentTaskTypeEnum.valueOf(task.getType());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("笔记正文同步任务类型不合法", e);
            }
        }
        syncCurrentContents(event.getContentTasks());
    }

    private void syncCurrentContents(List<NoteContentTaskMqDTO> tasks) {
        List<NoteContentTaskMqDTO> upsertTasks = new ArrayList<>();
        for (NoteContentTaskMqDTO task : tasks) {
            if (Objects.equals(task.getType(), NoteContentTaskTypeEnum.DELETE.name())) {
                if (!keyValueRpcService.deleteNoteContent(task.getContentUuid())) {
                    throw new IllegalStateException("笔记正文删除到 KV 失败");
                }
            } else {
                upsertTasks.add(task);
            }
        }
        if (upsertTasks.isEmpty()) {
            return;
        }

        // 事件内任务归属同一笔记，写前校验共享一次查询
        NoteDO current = noteDOMapper.selectByPrimaryKey(upsertTasks.get(0).getNoteId());
        List<NoteContentTaskMqDTO> toSaveTasks = new ArrayList<>();
        for (NoteContentTaskMqDTO task : upsertTasks) {
            if (matchesCurrentContent(current, task)) {
                toSaveTasks.add(task);
            } else {
                keyValueRpcService.deleteNoteContent(task.getContentUuid());
            }
        }
        for (NoteContentTaskMqDTO task : toSaveTasks) {
            if (StringUtils.isBlank(task.getContent())
                    || !keyValueRpcService.saveNoteContent(task.getContentUuid(), task.getContent())) {
                throw new IllegalStateException("笔记正文同步到 KV 失败");
            }
        }

        // 删除可能发生在写前校验和 KV 写入之间；写后复核并清理刚写入的旧正文。
        NoteDO after = noteDOMapper.selectByPrimaryKey(upsertTasks.get(0).getNoteId());
        toSaveTasks.stream()
                .filter(task -> !matchesCurrentContent(after, task))
                .forEach(task -> keyValueRpcService.deleteNoteContent(task.getContentUuid()));
    }

    private boolean matchesCurrentContent(NoteDO note, NoteContentTaskMqDTO task) {
        return Objects.nonNull(note) && StringUtils.equals(note.getContentUuid(), task.getContentUuid());
    }
}
