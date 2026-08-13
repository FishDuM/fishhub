package hk.ljx.fishhub.oss.biz.strategy.impl;

import com.aliyun.oss.OSS;
import hk.ljx.fishhub.oss.biz.config.AliyunOSSProperties;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.net.URI;


@Slf4j
public class AliyunOSSFileStrategy implements FileStrategy  {

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;

    @Resource
    private OSS ossClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("## 上传文件至阿里云 OSS ...");

        // 判断文件是否为空
        if (file == null || file.getSize() == 0) {
            log.error("==> 上传文件异常：文件大小为空 ...");
            throw new RuntimeException("文件大小不能为空");
        }

        // 文件的原始名称
        String originalFileName = file.getOriginalFilename();

        // 生成存储对象的名称（将 UUID 字符串中的 - 替换成空字符串）
        String key = UUID.randomUUID().toString().replace("-", "");
        // 获取文件的后缀，如 .jpg
        String suffix = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();

        // 拼接上文件后缀，即为要存储的文件名
        String objectName = String.format("%s%s", key, suffix);

        log.info("==> 开始上传文件至阿里云 OSS, ObjectName: {}", objectName);

        // 上传文件至阿里云 OSS
        // 直接流式上传，避免大文件整体读入 JVM 堆。
        ossClient.putObject(bucketName, objectName, file.getInputStream());

        // 返回文件的访问链接
        String url = String.format("https://%s.%s/%s", bucketName, aliyunOSSProperties.getEndpoint(), objectName);
        log.info("==> 上传文件至阿里云 OSS 成功，访问路径: {}", url);
        return url;
    }

    @Override
    public void deleteFile(String fileUrl, String bucketName) {
        String path = URI.create(fileUrl).getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException("文件地址不合法");
        }
        String objectName = path.substring(1);
        ossClient.deleteObject(bucketName, objectName);
        log.info("==> 阿里云 OSS 文件删除成功, ObjectName: {}", objectName);
    }
}
