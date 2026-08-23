package hk.ljx.framework.biz.context.config;

import hk.ljx.framework.biz.context.filter.HeaderUserId2ContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
public class ContextAutoConfiguration {

    @Bean
    public FilterRegistrationBean<HeaderUserId2ContextFilter> headerUserId2ContextFilterRegistration() {
        return new FilterRegistrationBean<>(new HeaderUserId2ContextFilter());
    }
}
