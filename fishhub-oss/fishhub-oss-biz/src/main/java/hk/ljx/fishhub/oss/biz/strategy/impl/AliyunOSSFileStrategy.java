package hk.ljx.fishhub.oss.biz.strategy.impl;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import hk.ljx.fishhub.oss.biz.config.AliyunOSSProperties;
import hk.ljx.fishhub.oss.biz.model.vo.PresignedUrlRspVO;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;


@Slf4j
@RequiredArgsConstructor
public class AliyunOSSFileStrategy implements FileStrategy  {

    private static final Pattern OWNED_OBJECT_NAME = Pattern.compile("user/\\d+/[a-f0-9]{32}\\.[a-z0-9]+");

    private final AliyunOSSProperties aliyunOSSProperties;

    private final OSS ossClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName, Long ownerId) {
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
        String suffix = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase(Locale.ROOT);

        // 拼接上文件后缀，即为要存储的文件名
        String objectName = String.format("user/%d/%s%s", ownerId, key, suffix);

        log.info("==> 开始上传文件至阿里云 OSS, ObjectName: {}", objectName);

        // 上传文件至阿里云 OSS
        // 直接流式上传，避免大文件整体读入 JVM 堆，使用 try-with-resources 确保输入流正确关闭。
        try (java.io.InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(bucketName, objectName, inputStream);
        }

        // 返回文件的访问链接
        String url = String.format("https://%s.%s/%s", bucketName, aliyunOSSProperties.getEndpoint(), objectName);
        log.info("==> 上传文件至阿里云 OSS 成功，访问路径: {}", url);
        return url;
    }

    @Override
    public void deleteFile(String fileUrl, String bucketName, Long ownerId) {
        URI uri = URI.create(fileUrl);
        String expectedHost = bucketName + "." + aliyunOSSProperties.getEndpoint();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !expectedHost.equalsIgnoreCase(uri.getHost())
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("文件地址不属于当前 OSS Bucket");
        }
        String path = uri.getRawPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException("文件地址不合法");
        }
        String objectName = path.substring(1);
        if (!objectName.startsWith("user/" + ownerId + "/") || !OWNED_OBJECT_NAME.matcher(objectName).matches()) {
            throw new IllegalArgumentException("无权删除该文件");
        }
        ossClient.deleteObject(bucketName, objectName);
        log.info("==> 阿里云 OSS 文件删除成功, ObjectName: {}", objectName);
    }

    @Override
    public PresignedUrlRspVO getPresignedUploadUrl(String fileName, String contentType, String bucketName, Long ownerId) {
        String key = UUID.randomUUID().toString().replace("-", "");
        String suffix = fileName != null && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")).toLowerCase(Locale.ROOT) : "";
        String objectName = String.format("user/%d/%s%s", ownerId, key, suffix);

        Date expiration = new Date(System.currentTimeMillis() + 10 * 60 * 1000L);
        URL uploadUrl = ossClient.generatePresignedUrl(bucketName, objectName, expiration, HttpMethod.PUT);

        String downloadUrl = String.format("https://%s.%s/%s", bucketName, aliyunOSSProperties.getEndpoint(), objectName);
        return PresignedUrlRspVO.builder()
                .uploadUrl(uploadUrl.toString())
                .downloadUrl(downloadUrl)
                .build();
    }
}
