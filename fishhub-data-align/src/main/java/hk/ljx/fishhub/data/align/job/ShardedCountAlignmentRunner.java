package hk.ljx.fishhub.data.align.job;

import com.xxl.job.core.context.XxlJobHelper;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.SelectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Component
@Slf4j
@RequiredArgsConstructor
public class ShardedCountAlignmentRunner {

    private static final int BATCH_SIZE = 1_000;
    private static final long USER_COUNT_CACHE_VERSION_EXPIRE_SECONDS = 3 * 60 * 60L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SelectMapper selectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${table.shards}")
    private int tableShards;

    public void run(String description,
                    String temporaryTablePrefix,
                    Function<String, List<Long>> loadBatch,
                    ToLongFunction<Long> loadAuthoritativeTotal,
                    BiFunction<Long, Long, Integer> updateTotal,
                    Function<Long, String> cacheKeyBuilder,
                    String cacheField,
                    BiConsumer<String, List<Long>> deleteBatch) {
        run(description, temporaryTablePrefix, loadBatch, loadAuthoritativeTotal, updateTotal,
                (id, total) -> updateExistingHashCache(cacheKeyBuilder.apply(id), cacheField, total), deleteBatch);
    }

    public void runUserCount(String description,
                             String temporaryTablePrefix,
                             Function<String, List<Long>> loadBatch,
                             ToLongFunction<Long> loadAuthoritativeTotal,
                             BiFunction<Long, Long, Integer> updateTotal,
                             BiConsumer<String, List<Long>> deleteBatch) {
        run(description, temporaryTablePrefix, loadBatch, loadAuthoritativeTotal, updateTotal,
                (id, total) -> advanceUserCountCacheVersion(id), deleteBatch);
    }

    private void run(String description,
                     String temporaryTablePrefix,
                     Function<String, List<Long>> loadBatch,
                     ToLongFunction<Long> loadAuthoritativeTotal,
                     BiFunction<Long, Long, Integer> updateTotal,
                     BiConsumer<Long, Long> afterDatabaseUpdate,
                     BiConsumer<String, List<Long>> deleteBatch) {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        int[] shardRange = TableConstants.computeShardRange(shardIndex, shardTotal, tableShards);
        String date = LocalDate.now().minusDays(1).format(DATE_FORMATTER);
        int processedTotal = 0;

        XxlJobHelper.log("开始分片对齐：{}，分片 {}/{}", description, shardIndex, shardTotal);
        for (int shard = shardRange[0]; shard < shardRange[1]; shard++) {
            String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shard);
            if (selectMapper.selectTempTableExists(temporaryTablePrefix + tableNameSuffix) == 0) {
                XxlJobHelper.log("日增量表不存在，跳过分片: {}", tableNameSuffix);
                continue;
            }

            while (true) {
                List<Long> ids = loadBatch.apply(tableNameSuffix);
                if (ids == null || ids.isEmpty()) {
                    break;
                }

                for (Long id : ids) {
                    long total = loadAuthoritativeTotal.applyAsLong(id);
                    updateTotal.apply(id, total);
                    afterDatabaseUpdate.accept(id, total);
                }
                deleteBatch.accept(tableNameSuffix, ids);
                processedTotal += ids.size();
            }
        }
        log.info("分片对齐完成：{}，处理 {} 条记录", description, processedTotal);
        XxlJobHelper.log("分片对齐完成：{}，处理 {} 条记录", description, processedTotal);
    }

    public int batchSize() {
        return BATCH_SIZE;
    }

    private void updateExistingHashCache(String cacheKey, String cacheField, long total) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            redisTemplate.opsForHash().put(cacheKey, cacheField, total);
        }
    }

    private void advanceUserCountCacheVersion(Long userId) {
        String versionKey = RedisKeyConstants.buildCountUserCacheVersionKey(userId);
        redisTemplate.opsForValue().increment(versionKey);
        redisTemplate.expire(versionKey, USER_COUNT_CACHE_VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }
}
