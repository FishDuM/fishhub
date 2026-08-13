package hk.ljx.fishhub.data.align.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.data.align.constant.MQConstants;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.InsertMapper;
import hk.ljx.fishhub.data.align.model.dto.LikeUnlikeNoteMqDTO;
import hk.ljx.fishhub.data.align.service.DailyChangeDeduplicator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;



@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_data_align_" + MQConstants.TOPIC_COUNT_NOTE_LIKE, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_LIKE // 主题 Topic
        )
@Slf4j
public class TodayNoteLikeIncrementData2DBConsumer implements RocketMQListener<String> {

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
        log.info("## TodayNoteLikeIncrementData2DBConsumer 消费到了 MQ: {}", body);

        // 消息体 JSON 字符串转 DTO
        LikeUnlikeNoteMqDTO unlikeNoteMqDTO = JsonUtils.parseObject(body, LikeUnlikeNoteMqDTO.class);

        if (Objects.isNull(unlikeNoteMqDTO)
                || unlikeNoteMqDTO.getNoteId() == null
                || unlikeNoteMqDTO.getNoteCreatorId() == null) {
            throw new IllegalArgumentException("笔记点赞对齐消息缺少业务主键");
        }

        // 被点赞、取消点赞的笔记 ID
        Long noteId = unlikeNoteMqDTO.getNoteId();
        // 笔记的发布者 ID
        Long noteCreatorId = unlikeNoteMqDTO.getNoteCreatorId();

        // 今日日期
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 格式化

        // ------------------------- 笔记的点赞数变更记录 -------------------------
        String noteDedupKey = RedisKeyConstants.buildDailyNoteLikeNoteIdsDedupKey(date);

        if (!deduplicator.exists(noteDedupKey, noteId)) {
            // 2. 若无，才会落库，减轻数据库压力

            // 根据分片总数，取模，获取对应的分片序号
            long noteIdHashKey = noteId % tableShards;

            // 将日增量变更数据落库
            // - t_data_align_note_like_count_temp_日期_分片序号
            insertRecordMapper.insert2DataAlignNoteLikeCountTempTable(TableConstants.buildTableNameSuffix(date, noteIdHashKey), noteId);

            deduplicator.markAfterDatabaseSuccess(noteDedupKey, noteId);
        }

        // ------------------------- 笔记发布者获得的点赞数变更记录 -------------------------
        String userDedupKey = RedisKeyConstants.buildDailyNoteLikeUserIdsDedupKey(date);
        if (!deduplicator.exists(userDedupKey, noteCreatorId)) {
            // 2. 若无，才会落库，减轻数据库压力

            // 根据分片总数，取模，获取对应的分片序号
            long userIdHashKey = noteCreatorId % tableShards;

            // 将日增量变更数据落库
            // - t_data_align_user_like_count_temp_日期_分片序号
            insertRecordMapper.insert2DataAlignUserLikeCountTempTable(TableConstants.buildTableNameSuffix(date, userIdHashKey), noteCreatorId);

            deduplicator.markAfterDatabaseSuccess(userDedupKey, noteCreatorId);
        }
    }
}
