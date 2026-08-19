package hk.ljx.fishhub.user.config;

import hk.ljx.fishhub.user.api.UserFeignApi;
import hk.ljx.fishhub.user.client.UserClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 用户服务 RPC 客户端自动配置
 */
@AutoConfiguration
public class UserClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UserClient userClient(UserFeignApi userFeignApi) {
        return new UserClient(userFeignApi);
    }
}
