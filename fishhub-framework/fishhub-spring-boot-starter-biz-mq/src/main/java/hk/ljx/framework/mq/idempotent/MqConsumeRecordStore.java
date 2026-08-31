package hk.ljx.framework.mq.idempotent;

import hk.ljx.framework.mq.idempotent.mapper.MqConsumeRecordDOMapper;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * t_mq_consume_record 消费幂等记录存储（统一由 MyBatis 实现）。
 */
@RequiredArgsConstructor
public class MqConsumeRecordStore {

    private final MqConsumeRecordDOMapper mapper;

    public int exists(String consumerGroup, String messageKey) {
        return mapper.exists(consumerGroup, messageKey);
    }

    public int insert(String consumerGroup, String messageKey) {
        return mapper.insert(consumerGroup, messageKey);
    }

    /**
     * 查询一批键中已存在的 message_key（事件级幂等去重依据）。
     */
    public List<String> findExisting(String consumerGroup, List<String> messageKeys) {
        if (messageKeys == null || messageKeys.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.findExisting(consumerGroup, messageKeys);
    }

    /**
     * 批量 INSERT IGNORE；返回实际插入行数（跳过已存在的键）。
     */
    public int insertIgnoreBatch(String consumerGroup, List<String> messageKeys) {
        if (messageKeys == null || messageKeys.isEmpty()) {
            return 0;
        }
        return mapper.insertIgnoreBatch(consumerGroup, messageKeys);
    }

    public int purgeOlderThanDays(int days, int batchSize) {
        return mapper.purgeOlderThanDays(days, batchSize);
    }
}
