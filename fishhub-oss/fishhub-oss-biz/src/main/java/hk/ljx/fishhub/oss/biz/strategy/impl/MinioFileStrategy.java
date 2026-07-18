package hk.ljx.fishhub.oss.biz.strategy.impl;

import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class MinioFileStrategy implements FileStrategy {

    @Override
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("minio 文件上传");
        return "";
    }
}
