package hk.ljx.fishhub.oss.biz.factory;

import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import hk.ljx.fishhub.oss.biz.strategy.impl.AliyunOSSFileStrategy;
import hk.ljx.fishhub.oss.biz.strategy.impl.MinioFileStrategy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import jakarta.annotation.Resource;

@Configuration
@RefreshScope
public class FileStrategyFactory {

    @Resource
    private AutowireCapableBeanFactory beanFactory;

    @Value("${storage.type}")
    private String storageType;

    @Bean
    @RefreshScope
    public FileStrategy getFileStrategy() {
        if (StringUtils.equals("aliyun", storageType)) {
            AliyunOSSFileStrategy strategy = new AliyunOSSFileStrategy();
            beanFactory.autowireBean(strategy);
            return strategy;
        } else if (StringUtils.equals("minio", storageType)) {
            MinioFileStrategy strategy = new MinioFileStrategy();
            beanFactory.autowireBean(strategy);
            return strategy;
        }

        throw new IllegalArgumentException("不可用的存储类型");
    }
}
