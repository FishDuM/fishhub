package hk.ljx.fishhub.oss.biz.factory;

import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import hk.ljx.fishhub.oss.biz.strategy.impl.AliyunOSSFileStrategy;
import hk.ljx.fishhub.oss.biz.strategy.impl.MinioFileStrategy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStrategyFactory {

    @Value("${storage.type}")
    private String storageType;

    public FileStrategy getFileStrategy() {
        if (StringUtils.equals("aliyun", storageType)) {
            return new AliyunOSSFileStrategy();
        } else if (StringUtils.equals("minio", storageType)) {
            return new MinioFileStrategy();
        }

        throw new IllegalArgumentException("不可用的存储类型");
    }
}
