package hk.ljx.fishhub.data.align.job;

import cn.hutool.core.collection.CollUtil;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.DeleteMapper;
import hk.ljx.fishhub.data.align.domain.mapper.SelectMapper;
import hk.ljx.fishhub.data.align.domain.mapper.UpdateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class FollowingCountShardingXxlJob {

    @Resource
    private SelectMapper selectMapper;

    @Resource
    private UpdateMapper updateMapper;

    @Resource
    private DeleteMapper deleteMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 分片广播任务
     */
    @XxlJob("followingCountShardingJobHandler")
    public void followingCountShardingJobHandler() throws Exception {
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的用户关注数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);
        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 表后缀
        String date = LocalDate.now().minusDays(1) // 昨日的日期
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串
        // 表名后缀
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);
        // 一批次 1000 条
        int batchSize = 1000;
        // 共对齐了多少条记录，默认为 0
        int processedTotal = 0;

        // 死循环
        for (;;) {
            // 分批次查询，如一批次查询 1000 条，直到全部查询完成
            List<Long> userIds = selectMapper.selectBatchFromDataAlignFollowingCountTempTable(tableNameSuffix, batchSize);
            // 若记录为空，终止循环
            if (CollUtil.isEmpty(userIds)) break;
            // 循环这一批发生变更的用户 ID
            userIds.forEach(userId -> {
                // 获取关注总数
                int followingTotal = selectMapper.selectCountFromFollowingTableByUserId(userId);
                // 更新 t_user_count 表
                int count = updateMapper.updateUserFollowingTotalByUserId(userId, followingTotal);
                // 更新对应 Redis 缓存
                if (count > 0) {
                    String redisKey = RedisKeyConstants.buildCountUserKey(userId);
                    boolean hashKey = redisTemplate.hasKey(redisKey);
                    if (hashKey) {
                        // 更新 Hash 中的 Field 关注总数
                        redisTemplate.opsForHash().put(redisKey, RedisKeyConstants.FIELD_FOLLOWING_TOTAL, followingTotal);
                    }
                }
            });

            // 批量物理删除这一批次记录
            deleteMapper.batchDeleteDataAlignFollowingCountTempTable(tableNameSuffix, userIds);
            // 当前已处理的记录数
            processedTotal += userIds.size();
        }
        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的用户关注数进行对齐，共对齐记录数：{}", processedTotal);
    }

}
