package hk.ljx.framework.mq.config;

import hk.ljx.framework.mq.idempotent.MqConsumeRecordPurgeJob;
import hk.ljx.framework.mq.idempotent.MqConsumeRecordStore;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.framework.mq.idempotent.mapper.MqConsumeRecordDOMapper;
import hk.ljx.framework.mq.consumer.BatchConsumerFactory;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalPurgeJob;
import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.framework.mq.tx.TxMqLocalTransactionListener;
import hk.ljx.framework.mq.tx.mapper.TxJournalDOMapper;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass({RocketMQTemplate.class, MqConsumeRecordDOMapper.class})
@AutoConfigureAfter({RocketMQAutoConfiguration.class, DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class})
@MapperScan(basePackages = {"hk.ljx.framework.mq.idempotent.mapper", "hk.ljx.framework.mq.tx.mapper"})
public class TxMqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TxJournalStore txJournalStore(TxJournalDOMapper txJournalDOMapper) {
        return new TxJournalStore(txJournalDOMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionalMqSender transactionalMqSender(RocketMQTemplate rocketMQTemplate) {
        return new TransactionalMqSender(rocketMQTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TxMqLocalTransactionListener.class)
    public TxMqLocalTransactionListener txMqLocalTransactionListener(TxJournalStore txJournalStore) {
        return new TxMqLocalTransactionListener(txJournalStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqConsumeRecordStore mqConsumeRecordStore(MqConsumeRecordDOMapper mqConsumeRecordDOMapper) {
        return new MqConsumeRecordStore(mqConsumeRecordDOMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public BatchConsumerFactory batchConsumerFactory(@Value("${rocketmq.name-server:127.0.0.1:9876}") String namesrvAddr) {
        return new BatchConsumerFactory(namesrvAddr);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqIdempotentExecutor mqIdempotentExecutor(MqConsumeRecordStore mqConsumeRecordStore, DataSource dataSource) {
        return new MqIdempotentExecutor(mqConsumeRecordStore,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @Bean
    @ConditionalOnMissingBean
    public MqConsumeRecordPurgeJob mqConsumeRecordPurgeJob(MqConsumeRecordStore mqConsumeRecordStore,
            @Value("${mq.consume-record.retention-days:7}") int retentionDays) {
        return new MqConsumeRecordPurgeJob(mqConsumeRecordStore, retentionDays);
    }

    @Bean
    @ConditionalOnMissingBean
    public TxJournalPurgeJob txJournalPurgeJob(TxJournalDOMapper txJournalDOMapper,
            @Value("${mq.tx-journal.retention-hours:24}") int retentionHours) {
        return new TxJournalPurgeJob(txJournalDOMapper, retentionHours);
    }
}
