package hk.ljx.framework.mq.idempotent;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * t_mq_consume_record 消费幂等记录存储（JdbcTemplate 实现，不依赖 MyBatis）。
 */
public class MqConsumeRecordStore {

    private final JdbcTemplate jdbcTemplate;

    public MqConsumeRecordStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int exists(String consumerGroup, String messageKey) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from t_mq_consume_record where consumer_group = ? and message_key = ?",
                Integer.class, consumerGroup, messageKey);
        return count == null ? 0 : count;
    }

    public int insert(String consumerGroup, String messageKey) {
        return jdbcTemplate.update(
                "insert into t_mq_consume_record (consumer_group, message_key, create_time) values (?, ?, now())",
                consumerGroup, messageKey);
    }

    public int purgeOlderThanDays(int days, int batchSize) {
        return jdbcTemplate.update(
                "delete from t_mq_consume_record where create_time < date_sub(now(), interval ? day) limit ?",
                days, batchSize);
    }
}
