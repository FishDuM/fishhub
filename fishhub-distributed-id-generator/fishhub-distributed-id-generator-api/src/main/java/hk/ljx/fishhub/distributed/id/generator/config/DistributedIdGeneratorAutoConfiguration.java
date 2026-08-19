package hk.ljx.fishhub.distributed.id.generator.config;

import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import hk.ljx.fishhub.distributed.id.generator.client.DistributedIdGeneratorClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 分布式发号客户端自动装配配置
 */
@AutoConfiguration
public class DistributedIdGeneratorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedIdGeneratorClient distributedIdGeneratorClient(DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi) {
        return new DistributedIdGeneratorClient(distributedIdGeneratorFeignApi);
    }
}
