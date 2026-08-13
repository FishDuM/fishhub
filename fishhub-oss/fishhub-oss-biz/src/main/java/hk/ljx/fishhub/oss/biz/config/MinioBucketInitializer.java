package hk.ljx.fishhub.oss.biz.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 社区图片使用普通对象 URL，启动时确保 fishhub bucket 允许匿名只读。 */
@Component
@Slf4j
public class MinioBucketInitializer implements ApplicationRunner {

    @Resource
    private MinioClient minioClient;

    @Value("${storage.minio.public-read-bucket:fishhub}")
    private String publicReadBucket;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(publicReadBucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(publicReadBucket).build());
            }
            String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                    + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
                    + publicReadBucket + "/*\"]}]}";
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(publicReadBucket)
                    .config(policy)
                    .build());
            log.info("MinIO bucket {} 已配置匿名只读", publicReadBucket);
        } catch (Exception e) {
            log.error("无法为 MinIO bucket {} 配置匿名读策略；OSS 将继续启动，请检查 storage.minio 的访问凭据", publicReadBucket, e);
        }
    }
}
