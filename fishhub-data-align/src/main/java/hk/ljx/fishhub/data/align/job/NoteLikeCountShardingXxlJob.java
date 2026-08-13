package hk.ljx.fishhub.data.align.job;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.DeleteMapper;
import hk.ljx.fishhub.data.align.domain.mapper.SelectMapper;
import hk.ljx.fishhub.data.align.domain.mapper.UpdateMapper;
import hk.ljx.fishhub.data.align.rpc.SearchRpcService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Component
@Slf4j
public class NoteLikeCountShardingXxlJob {

    @Resource
    private SelectMapper selectMapper;
    @Resource
    private UpdateMapper updateMapper;
    @Resource
    private DeleteMapper deleteMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private SearchRpcService searchRpcService;

    /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int tableShards;

    /**
     * 分片广播任务
     */
    @XxlJob("noteLikeCountShardingJobHandler")
    public void noteLikeCountShardingJobHandler() throws Exception {
        // 获取分片参数
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的笔记点赞数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 单实例遍历全部分片表，多实例每台执行器处理一个分片
        int[] shardRange = TableConstants.computeShardRange(shardIndex, shardTotal, tableShards);

        // 昨日的日期
        String date = LocalDate.now().minusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 共对齐了多少条记录，默认为 0
        int processedTotal = 0;

        // 遍历本次任务需处理的分片表
        for (int shard = shardRange[0]; shard < shardRange[1]; shard++) {
            // 表名后缀
            String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shard);

            // 日增量表不存在则跳过（如建表任务未执行），避免整个任务失败
            if (selectMapper.selectTempTableExists("t_data_align_note_like_count_temp_" + tableNameSuffix) == 0) {
                XxlJobHelper.log("日增量表不存在，跳过分片: {}", tableNameSuffix);
                continue;
            }

            // 一批次 1000 条
            int batchSize = 1000;

            // 死循环
            for (;;) {
                // 1. 分批次查询 t_data_align_note_like_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
                List<Long> noteIds = selectMapper.selectBatchFromDataAlignNoteLikeCountTempTable(tableNameSuffix, batchSize);

                // 若记录为空，终止循环
                if (CollUtil.isEmpty(noteIds)) break;

                // 循环这一批发生变更的笔记 ID
                noteIds.forEach(noteId -> {
                    // 以点赞关系表为准重新计算笔记点赞数
                    long likeTotal = selectMapper.selectCountFromNoteLikeTableByUserId(noteId);

                    // 3: 更新 t_note_count 表, 更新对应 Redis 缓存
                    int count = updateMapper.updateNoteLikeTotalByUserId(noteId, likeTotal);
                    // 更新对应 Redis 缓存
                    if (count > 0) {
                        String redisKey = RedisKeyConstants.buildCountNoteKey(noteId);
                        // 判断 Hash 是否存在
                        boolean hashKey = redisTemplate.hasKey(redisKey);
                        // 若存在
                        if (hashKey) {
                            // 更新 Hash 中的 Field 点赞总数
                            redisTemplate.opsForHash().put(redisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, likeTotal);
                        }
                    }

                    // 远程 RPC, 调用搜索服务，重新构建文档
                    searchRpcService.rebuildNoteDocument(noteId);
                });

                // 4. 批量物理删除这一批次记录
                deleteMapper.batchDeleteDataAlignNoteLikeCountTempTable(tableNameSuffix, noteIds);

                // 当前已处理的记录数
                processedTotal += noteIds.size();
            }
        }

        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的笔记点赞数进行对齐，共对齐记录数：{}", processedTotal);
    }

}
