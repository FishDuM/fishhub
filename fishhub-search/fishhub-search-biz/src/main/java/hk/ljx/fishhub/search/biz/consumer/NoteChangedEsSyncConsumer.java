package hk.ljx.fishhub.search.biz.consumer;

import hk.ljx.framework.common.constant.MqTopicConstants;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.search.biz.service.EsIndexSyncAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 笔记变更事件 → ES 笔记索引同步。
 * 发布/更新/删除/可见性变更统一走聚合器：其内部按 DB 最新状态重建，不可检索的笔记自动删除文档。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_search_es_note_changed",
        topic = MqTopicConstants.TOPIC_NOTE_CHANGED)
@Slf4j
@RequiredArgsConstructor
public class NoteChangedEsSyncConsumer implements RocketMQListener<String> {

    private final EsIndexSyncAggregator esIndexSyncAggregator;

    @Override
    public void onMessage(String body) {
        Map<?, ?> event = JsonUtils.parseObject(body, Map.class);
        if (event == null || event.get("noteId") == null) {
            throw new IllegalArgumentException("笔记变更消息缺少 noteId");
        }
        Long noteId = Long.valueOf(String.valueOf(event.get("noteId")));
        esIndexSyncAggregator.submitNote(noteId);
    }
}
