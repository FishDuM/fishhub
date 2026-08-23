package hk.ljx.fishhub.oss.biz.factory;

import com.aliyun.oss.OSS;
import hk.ljx.fishhub.oss.biz.config.AliyunOSSProperties;
import hk.ljx.fishhub.oss.biz.config.MinioProperties;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import hk.ljx.fishhub.oss.biz.strategy.impl.AliyunOSSFileStrategy;
import hk.ljx.fishhub.oss.biz.strategy.impl.MinioFileStrategy;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RefreshScope
@RequiredArgsConstructor
public class FileStrategyFactory {

    @Value("${storage.type}")
    private String strategyType;

    private final ObjectProvider<MinioProperties> minioPropertiesProvider;
    private final ObjectProvider<MinioClient> minioClientProvider;
    private final ObjectProvider<AliyunOSSProperties> aliyunOSSPropertiesProvider;
    private final ObjectProvider<OSS> ossClientProvider;

    @Bean
    @RefreshScope
    public FileStrategy fileStrategy() {
        if (StringUtils.equals(strategyType, "minio")) {
            MinioProperties minioProperties = minioPropertiesProvider.getIfAvailable();
            MinioClient minioClient = minioClientProvider.getIfAvailable();
            if (minioProperties == null || minioClient == null) {
                throw new IllegalStateException("MinIO 存储策略所需组件 (MinioProperties / MinioClient) 未就绪，请检查相关配置");
            }
            return new MinioFileStrategy(minioProperties, minioClient);
        } else if (StringUtils.equals(strategyType, "aliyun")) {
            AliyunOSSProperties aliyunOSSProperties = aliyunOSSPropertiesProvider.getIfAvailable();
            OSS ossClient = ossClientProvider.getIfAvailable();
            if (aliyunOSSProperties == null || ossClient == null) {
                throw new IllegalStateException("阿里云 OSS 存储策略所需组件 (AliyunOSSProperties / OSS) 未就绪，请检查相关配置");
            }
            return new AliyunOSSFileStrategy(aliyunOSSProperties, ossClient);
        } else {
            throw new IllegalArgumentException("不可用的存储类型: " + strategyType);
        }
    }

}
