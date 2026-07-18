package hk.ljx.fishhub.oss.biz.strategy.impl;

import hk.ljx.fishhub.oss.biz.config.MinioProperties;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
public class MinioFileStrategy implements FileStrategy {

    @Resource
    private MinioProperties minioProperties;

    @Resource
    private MinioClient minioClient;

    @SneakyThrows
    @Override
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("minio 文件上传");

        // 判断文件是否为空
        if (file == null || file.getSize() == 0) {
            log.error("文件上传异常，文件为空");
            throw new RuntimeException("文件大小不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        // 生成文件名称
        String key = UUID.randomUUID().toString().replace("-", "");
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = String.format("%s%s", key, suffix);
        // 上传到的 minio
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(contentType)
                .build());

        // 返回文件的访问链接
        String url = String.format("%s/%s/%s", minioProperties.getEndpoint(), bucketName, objectName);
        log.info("上传文件至 minio 成功，访问路径: {}", url);
        return url;
    }
}
