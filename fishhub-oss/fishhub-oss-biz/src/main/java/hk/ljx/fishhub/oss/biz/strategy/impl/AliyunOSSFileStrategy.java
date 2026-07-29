package hk.ljx.fishhub.oss.biz.strategy.impl;

import com.aliyun.oss.OSS;
import hk.ljx.fishhub.oss.biz.config.AliyunOSSProperties;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Slf4j
public class AliyunOSSFileStrategy implements FileStrategy {

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;

    @Resource
    private OSS ossClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName) {
        if (file == null || file.getSize() == 0) {
            throw new IllegalArgumentException("文件大小不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        String suffix = originalFileName.substring(originalFileName.lastIndexOf("."));
        String objectName = UUID.randomUUID().toString().replace("-", "") + suffix;

        ossClient.putObject(bucketName, objectName,
                new ByteArrayInputStream(file.getInputStream().readAllBytes()));
        return String.format("https://%s.%s/%s", bucketName, aliyunOSSProperties.getEndpoint(), objectName);
    }
}
