package hk.ljx.fishhub.comment.biz.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * t_mq_consume_record 消费幂等记录滚动清理。
 * 幂等窗口只需覆盖 broker 重投周期（小时级），保留 7 天已有充足裕量；
 * 该表原先只增不删，是最大的单调增长隐患。
 */
@Slf4j
@Component
public class MqConsumeRecordPurgeJob {

    private static final int BATCH_SIZE = 5000;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${mq.consume-record.purge-cron:0 50 4 * * ?}")
    public void purge() {
        int total = 0;
        int deleted;
        do {
            deleted = jdbcTemplate.update(
                    "delete from t_mq_consume_record where create_time < date_sub(now(), interval 7 day) limit " + BATCH_SIZE);
            total += deleted;
        } while (deleted == BATCH_SIZE);
        if (total > 0) {
            log.info("t_mq_consume_record 清理完成, 删除 {} 行", total);
        }
    }
}
