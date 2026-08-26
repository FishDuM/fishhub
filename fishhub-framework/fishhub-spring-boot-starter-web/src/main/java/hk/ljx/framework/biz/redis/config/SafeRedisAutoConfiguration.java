package hk.ljx.framework.biz.redis.config;

import hk.ljx.framework.common.util.SafeRedisUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * SafeRedisUtil 自动装配配置类
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class SafeRedisAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(SafeRedisUtil.class)
    public SafeRedisUtil safeRedisUtil(StringRedisTemplate stringRedisTemplate) {
        return new SafeRedisUtil(stringRedisTemplate);
    }
}
