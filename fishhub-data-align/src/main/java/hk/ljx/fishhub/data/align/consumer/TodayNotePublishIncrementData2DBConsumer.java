package hk.ljx.fishhub.data.align.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.data.align.constant.MQConstants;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.InsertMapper;
import hk.ljx.fishhub.data.align.model.dto.NoteChangedEventMqDTO;
import hk.ljx.fishhub.data.align.service.DailyChangeDeduplicator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_data_align_" + MQConstants.TOPIC_NOTE_CHANGED, // Group 组
        topic = MQConstants.TOPIC_NOTE_CHANGED // 主题 Topic
        )
@Slf4j
public class TodayNotePublishIncrementData2DBConsumer implements RocketMQListener<String> {

    @Resource
    private InsertMapper insertRecordMapper;
    @Resource
    private DailyChangeDeduplicator deduplicator;

    /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int tableShards;

    @Override
    public void onMessage(String body) {
        log.info("## TodayNotePublishIncrementData2DBConsumer 消费到了 MQ: {}", body);

        // 消息体 JSON 字符串转 DTO
        NoteChangedEventMqDTO event = JsonUtils.parseObject(body, NoteChangedEventMqDTO.class);

        if (Objects.isNull(event) || event.getCreatorId() == null || event.getChangeType() == null) {
            throw new IllegalArgumentException("笔记操作对齐消息缺少用户 ID");
        }
        // 编辑不影响日增量，直接确认
        if (!Objects.equals(event.getChangeType(), 1) && !Objects.equals(event.getChangeType(), 0)) {
            return;
        }

        // 发布、被删除笔记发布者 ID
        Long noteCreatorId = event.getCreatorId();

        // 今日日期
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串

        String dedupKey = RedisKeyConstants.buildDailyNoteOperateUserIdsDedupKey(date);

        if (!deduplicator.exists(dedupKey, noteCreatorId)) {
            // 2. 若无，才会落库，减轻数据库压力

            // 根据分片总数，取模，分别获取对应的分片序号
            long userIdHashKey = noteCreatorId % tableShards;

            // 将日增量变更数据，写入日增量表中
            // - t_data_align_note_publish_count_temp_日期_分片序号
            insertRecordMapper.insert2DataAlignUserNotePublishCountTempTable(TableConstants.buildTableNameSuffix(date, userIdHashKey), noteCreatorId);

            deduplicator.markAfterDatabaseSuccess(dedupKey, noteCreatorId);
        }
    }
}
