package hk.ljx.fishhub.data.align.job;

import com.xxl.job.core.context.XxlJobContext;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.SelectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShardedCountAlignmentRunnerTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Mock
    private SelectMapper selectMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void shouldAdvanceUserCountCacheVersionAfterAligningDatabaseTotal() {
        ShardedCountAlignmentRunner runner = new ShardedCountAlignmentRunner(selectMapper, stringRedisTemplate);
        ReflectionTestUtils.setField(runner, "tableShards", 1);
        String tableSuffix = TableConstants.buildTableNameSuffix(
                LocalDate.now().minusDays(1).format(DATE_FORMATTER), 0);
        when(selectMapper.selectTempTableExists("t_data_align_user_like_count_temp_" + tableSuffix)).thenReturn(1);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        AtomicBoolean firstBatch = new AtomicBoolean(true);
        AtomicReference<Long> updatedUserId = new AtomicReference<>();
        AtomicReference<Long> updatedTotal = new AtomicReference<>();
        AtomicReference<List<Long>> deletedIds = new AtomicReference<>();
        BiFunction<Long, Long, Integer> updateTotal = (userId, total) -> {
            updatedUserId.set(userId);
            updatedTotal.set(total);
            return 1;
        };
        BiConsumer<String, List<Long>> deleteBatch = (suffix, ids) -> deletedIds.set(ids);

        XxlJobContext.setXxlJobContext(new XxlJobContext(1L, "", "", 0, 1));
        try {
            runner.runUserCount("用户获赞数", "t_data_align_user_like_count_temp_",
                    suffix -> firstBatch.getAndSet(false) ? List.of(100L) : List.of(),
                    id -> 8L, updateTotal, deleteBatch);
        } finally {
            XxlJobContext.setXxlJobContext(null);
        }

        assertEquals(100L, updatedUserId.get());
        assertEquals(8L, updatedTotal.get());
        verify(valueOperations).increment(RedisKeyConstants.buildCountUserCacheVersionKey(100L));
        verify(stringRedisTemplate).expire(RedisKeyConstants.buildCountUserCacheVersionKey(100L),
                3 * 60 * 60L, TimeUnit.SECONDS);
        assertEquals(List.of(100L), deletedIds.get());
        verify(selectMapper).selectTempTableExists(anyString());
    }
}
