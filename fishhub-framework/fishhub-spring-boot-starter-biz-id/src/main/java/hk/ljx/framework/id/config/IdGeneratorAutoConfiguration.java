package hk.ljx.framework.id.config;

import hk.ljx.framework.id.client.DistributedIdGeneratorClient;
import hk.ljx.framework.id.core.SnowflakeIdGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(IdGeneratorProperties.class)
public class IdGeneratorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator(IdGeneratorProperties properties) {
        return new SnowflakeIdGenerator(properties.getWorkerId(), properties.getDatacenterId());
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedIdGeneratorClient distributedIdGeneratorClient(SnowflakeIdGenerator snowflakeIdGenerator) {
        return new DistributedIdGeneratorClient(snowflakeIdGenerator);
    }
}
