package hk.ljx.framework.biz.operationlog.config;

import hk.ljx.framework.biz.operationlog.aspect.ApiOperationLogAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
public class ApiOperationLogAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "fishhub.operation-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApiOperationLogAspect apiOperationLogAspect() {
        return new ApiOperationLogAspect();
    }
}
