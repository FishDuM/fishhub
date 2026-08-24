package hk.ljx.fishhub.oss.biz.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "aliyun")
public class AliyunOSSConfig {

    private final AliyunOSSProperties aliyunOSSProperties;

    /**
     * 构建 阿里云 OSS 客户端
     *
     * @return
     */
    @Bean
    public OSS aliyunOSSClient() {
        // 设置访问凭证
        DefaultCredentialProvider credentialsProvider = CredentialsProviderFactory.newDefaultCredentialProvider(
                aliyunOSSProperties.getAccessKey(), aliyunOSSProperties.getSecretKey());

        // 创建 OSSClient 实例
        return new OSSClientBuilder().build(aliyunOSSProperties.getEndpoint(), credentialsProvider);
    }
}
