package hk.ljx.fishhub.oss.biz.strategy.impl;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import hk.ljx.fishhub.oss.biz.config.AliyunOSSProperties;
import hk.ljx.fishhub.oss.biz.model.vo.PresignedUrlRspVO;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;


@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "aliyun")
public class AliyunOSSFileStrategy implements FileStrategy  {

    private static final Pattern OWNED_OBJECT_NAME = Pattern.compile("user/\\d+/[a-f0-9]{32}\\.[a-z0-9]+");

    private final AliyunOSSProperties aliyunOSSProperties;

    private final OSS ossClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName, Long ownerId) {
        log.info("## 上传文件至阿里云 OSS ...");

        String originalFileName = file.getOriginalFilename();
        String key = IdUtil.fastSimpleUUID();
        String suffix = "." + FileNameUtil.extName(originalFileName).toLowerCase(Locale.ROOT);
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
        String key = IdUtil.fastSimpleUUID();
        String ext = FileNameUtil.extName(fileName);
        String suffix = ext == null || ext.isEmpty() ? "" : "." + ext.toLowerCase(Locale.ROOT);
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
