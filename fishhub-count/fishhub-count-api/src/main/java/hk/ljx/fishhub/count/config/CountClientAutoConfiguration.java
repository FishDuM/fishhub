package hk.ljx.fishhub.count.config;

import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.client.CountClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 计数服务 RPC 客户端自动配置
 */
@AutoConfiguration
public class CountClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CountClient countClient(CountFeignApi countFeignApi) {
        return new CountClient(countFeignApi);
    }
}
