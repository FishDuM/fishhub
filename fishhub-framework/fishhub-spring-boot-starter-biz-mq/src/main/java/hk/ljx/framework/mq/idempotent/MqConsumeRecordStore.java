package hk.ljx.framework.mq.idempotent;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /**
     * 查询一批键中已存在的 message_key（事件级幂等去重依据）。
     */
    public List<String> findExisting(String consumerGroup, List<String> messageKeys) {
        if (messageKeys == null || messageKeys.isEmpty()) {
            return List.of();
        }
        String inClause = String.join(",", Collections.nCopies(messageKeys.size(), "?"));
        List<Object> args = new ArrayList<>(messageKeys.size() + 1);
        args.add(consumerGroup);
        args.addAll(messageKeys);
        return jdbcTemplate.queryForList(
                "select message_key from t_mq_consume_record where consumer_group = ? and message_key in (" + inClause + ")",
                String.class, args.toArray());
    }

    /**
     * 批量 INSERT IGNORE；返回实际插入行数（跳过已存在的键）。
     */
    public int insertIgnoreBatch(String consumerGroup, List<String> messageKeys) {
        if (messageKeys == null || messageKeys.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder(
                "insert ignore into t_mq_consume_record (consumer_group, message_key, create_time) values ");
        List<Object> args = new ArrayList<>(messageKeys.size() * 2 + 1);
        for (int i = 0; i < messageKeys.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?, now())");
            args.add(consumerGroup);
            args.add(messageKeys.get(i));
        }
        return jdbcTemplate.update(sql.toString(), args.toArray());
    }

    public int purgeOlderThanDays(int days, int batchSize) {
        return jdbcTemplate.update(
                "delete from t_mq_consume_record where create_time < date_sub(now(), interval ? day) limit ?",
                days, batchSize);
    }
}
