package hk.ljx.fishhub.data.align.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.data.align.constant.MQConstants;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.InsertMapper;
import hk.ljx.fishhub.data.align.model.dto.FollowUnfollowMqDTO;
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
@RocketMQMessageListener(consumerGroup = "fishhub_group_data_align_" + MQConstants.TOPIC_COUNT_FOLLOWING, // Group 组
        topic = MQConstants.TOPIC_COUNT_FOLLOWING // 主题 Topic
        )
@Slf4j
public class TodayUserFollowIncrementData2DBConsumer implements RocketMQListener<String> {

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
        log.info("## TodayUserFollowIncrementData2DBConsumer 消费到了 MQ: {}", body);

        // 消息体 JSON 字符串转 DTO
        FollowUnfollowMqDTO followUnfollowMqDTO = JsonUtils.parseObject(body, FollowUnfollowMqDTO.class);

        if (Objects.isNull(followUnfollowMqDTO)
                || followUnfollowMqDTO.getUserId() == null
                || followUnfollowMqDTO.getTargetUserId() == null) {
            throw new IllegalArgumentException("关注关系对齐消息缺少业务主键");
        }

        // 关注/取关操作
        // 源用户 ID
        Long userId = followUnfollowMqDTO.getUserId();
        // 目标用户 ID
        Long targetUserId = followUnfollowMqDTO.getTargetUserId();

        // 今日日期
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串

        // ------------------------- 源用户的关注数变更记录 -------------------------
        String userDedupKey = RedisKeyConstants.buildDailyFollowingUserIdsDedupKey(date);

        if (!deduplicator.exists(userDedupKey, userId)) {
            // 若无，才会落库，减轻数据库压力
            // 根据分片总数，取模，分别获取对应的分片序号
            long userIdHashKey = userId % tableShards;

            // 将日增量变更数据，写入表 t_data_align_following_count_temp_日期_分片序号
            insertRecordMapper.insert2DataAlignUserFollowingCountTempTable(
                    TableConstants.buildTableNameSuffix(date, userIdHashKey), userId);

            deduplicator.markAfterDatabaseSuccess(userDedupKey, userId);
        }

        // ------------------------- 目标用户的粉丝数变更记录 -------------------------
        String targetUserDedupKey = RedisKeyConstants.buildDailyFansUserIdsDedupKey(date);

        if (!deduplicator.exists(targetUserDedupKey, targetUserId)) {
            // 若无，才会落库，减轻数据库压力
            // 根据分片总数，取模，分别获取对应的分片序号
            long targetUserIdHashKey = targetUserId % tableShards;

            // 将日增量变更数据，写入表 t_data_align_fans_count_temp_日期_分片序号
            insertRecordMapper.insert2DataAlignUserFansCountTempTable(
                    TableConstants.buildTableNameSuffix(date, targetUserIdHashKey), targetUserId);

            deduplicator.markAfterDatabaseSuccess(targetUserDedupKey, targetUserId);
        }
    }
}
