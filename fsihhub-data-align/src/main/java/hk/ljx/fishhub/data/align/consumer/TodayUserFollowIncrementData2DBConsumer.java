package hk.ljx.fishhub.data.align.consumer;

import hk.ljx.fishhub.data.align.constant.MQConstants;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.InsertRecordMapper;
import hk.ljx.fishhub.data.align.model.dto.FollowUnfollowMqDTO;
import hk.ljx.framework.common.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_data_align_" + MQConstants.TOPIC_COUNT_FOLLOWING, // Group 组
        topic = MQConstants.TOPIC_COUNT_FOLLOWING // 主题 Topic
        )
@Slf4j
public class TodayUserFollowIncrementData2DBConsumer implements RocketMQListener<String> {

    @Resource
    private InsertRecordMapper insertRecordMapper;

    /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int tableShards;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        log.info("## TodayUserFollowIncrementData2DBConsumer 消费到了 MQ: {}", body);
        FollowUnfollowMqDTO followUnfollowMqDTO = JsonUtils.parseObject(body, FollowUnfollowMqDTO.class);
        if (Objects.isNull(followUnfollowMqDTO)) return;

        // 关注/取关操作
        Long userId = followUnfollowMqDTO.getUserId();
        Long targetUserId = followUnfollowMqDTO.getTargetUserId();
        // 今日日期
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串

        // 源用户的关注数变更记录
        String userBloomKey = RedisKeyConstants.buildBloomUserFollowListKey(date);
        // 布隆过滤器判断该日增量数据是否已经记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_user_follow_check.lua")));
        script.setResultType(Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(userBloomKey), userId);
        // Lua 脚本：添加到布隆过滤器
        RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);
        // 若布隆过滤器判断不存在（绝对正确）
        if (Objects.equals(result, 0L)) {
            // 分别获取对应的分片序号
            long userIdHashKey = userId % tableShards;
            try {
                // 将日增量变更数据写入表
                insertRecordMapper.insert2DataAlignUserFollowingCountTempTable(
                        TableConstants.buildTableNameSuffix(date, userIdHashKey), userId);
            } catch (Exception e) {
                log.error("", e);
            }
            // 添加布隆过滤器中
            redisTemplate.execute(bloomAddScript, Collections.singletonList(userBloomKey), userId);
        }

        // 目标用户的粉丝数变更记录
        String targetUserBloomKey = RedisKeyConstants.buildBloomUserFansListKey(date);
        result = redisTemplate.execute(script, Collections.singletonList(targetUserBloomKey), targetUserId);

        // 若布隆过滤器判断不存在（绝对正确）
        if (Objects.equals(result, 0L)) {
            // 分别获取对应的分片序号
            long targetUserIdHashKey = targetUserId % tableShards;
            try {
                // 将日增量变更数据写入表
                insertRecordMapper.insert2DataAlignUserFansCountTempTable(
                        TableConstants.buildTableNameSuffix(date, targetUserIdHashKey), targetUserId);
            } catch (Exception e) {
                log.error("", e);
            }
            // 添加布隆过滤器中
            redisTemplate.execute(bloomAddScript, Collections.singletonList(targetUserBloomKey), targetUserId);
        }
    }
}
