package hk.ljx.framework.id.config;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import hk.ljx.framework.id.client.DistributedIdGeneratorClient;
import hk.ljx.framework.id.core.SnowflakeIdGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@EnableConfigurationProperties(IdGeneratorProperties.class)
public class IdGeneratorAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public Snowflake snowflake(IdGeneratorProperties properties) {
        long workerId;
        if (properties.getWorkerId() != null) {
            workerId = properties.getWorkerId();
        } else {
            workerId = NetUtil.ipv4ToLong(NetUtil.getLocalhostStr()) & 0x1F;
        }
        long datacenterId = properties.getDatacenterId() != null ? properties.getDatacenterId() : 1L;
        return IdUtil.getSnowflake(workerId, datacenterId);
    }

    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator(IdGeneratorProperties properties) {
        return new SnowflakeIdGenerator(properties.getWorkerId(), properties.getDatacenterId());
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedIdGeneratorClient distributedIdGeneratorClient(Snowflake snowflake) {
        return new DistributedIdGeneratorClient(snowflake);
    }
}
