package hk.ljx.framework.mq.config;

import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalPurgeJob;
import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.framework.mq.tx.TxMqLocalTransactionListener;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(RocketMQTemplate.class)
@AutoConfigureAfter({RocketMQAutoConfiguration.class, DataSourceAutoConfiguration.class})
public class TxMqAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public TxJournalStore txJournalStore(DataSource dataSource) {
        return new TxJournalStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionalMqSender transactionalMqSender(RocketMQTemplate rocketMQTemplate) {
        return new TransactionalMqSender(rocketMQTemplate);
    }

    @Bean
    @ConditionalOnBean(TxJournalStore.class)
    @ConditionalOnMissingBean(TxMqLocalTransactionListener.class)
    public TxMqLocalTransactionListener txMqLocalTransactionListener(TxJournalStore txJournalStore) {
        return new TxMqLocalTransactionListener(txJournalStore);
    }

    @Bean
    @ConditionalOnBean(TxJournalStore.class)
    @ConditionalOnMissingBean
    public TxJournalPurgeJob txJournalPurgeJob(DataSource dataSource,
            @Value("${mq.tx-journal.retention-hours:24}") int retentionHours) {
        return new TxJournalPurgeJob(new JdbcTemplate(dataSource), retentionHours);
    }
}
