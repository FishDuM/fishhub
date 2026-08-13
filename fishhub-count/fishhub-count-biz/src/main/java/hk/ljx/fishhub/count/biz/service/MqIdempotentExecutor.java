package hk.ljx.fishhub.count.biz.service;

import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.fishhub.count.biz.domain.mapper.MqConsumeRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MqIdempotentExecutor {

    @Resource
    private MqConsumeRecordMapper mqConsumeRecordMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    public boolean execute(String consumerGroup, String messageIdentity, Runnable databaseAction) {
        String messageKey = DigestUtil.sha256Hex(messageIdentity);
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (mqConsumeRecordMapper.exists(consumerGroup, messageKey) > 0) {
                return false;
            }
            try {
                mqConsumeRecordMapper.insert(consumerGroup, messageKey);
            } catch (DuplicateKeyException e) {
                return false;
            }
            databaseAction.run();
            return true;
        }));
    }
}
