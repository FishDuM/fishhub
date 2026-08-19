package hk.ljx.fishhub.kv.config;

import hk.ljx.fishhub.kv.api.KeyValueFeignApi;
import hk.ljx.fishhub.kv.client.KeyValueClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * KV 存储服务 RPC 客户端自动配置
 */
@AutoConfiguration
public class KeyValueClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyValueClient keyValueClient(KeyValueFeignApi keyValueFeignApi) {
        return new KeyValueClient(keyValueFeignApi);
    }
}
