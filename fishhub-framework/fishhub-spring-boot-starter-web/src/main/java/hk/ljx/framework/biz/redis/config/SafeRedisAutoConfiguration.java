package hk.ljx.framework.biz.redis.config;

import hk.ljx.framework.common.util.SafeRedisUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * SafeRedisUtil 自动装配配置类
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
public class SafeRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SafeRedisUtil.class)
    public SafeRedisUtil safeRedisUtil(StringRedisTemplate stringRedisTemplate) {
        return new SafeRedisUtil(stringRedisTemplate);
    }
}
