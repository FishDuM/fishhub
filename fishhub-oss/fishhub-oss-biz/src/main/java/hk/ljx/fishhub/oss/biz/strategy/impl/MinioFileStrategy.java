package hk.ljx.fishhub.oss.biz.strategy.impl;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import hk.ljx.fishhub.oss.biz.config.MinioProperties;
import hk.ljx.fishhub.oss.biz.model.vo.PresignedUrlRspVO;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioFileStrategy implements FileStrategy  {

    private static final Pattern OWNED_OBJECT_NAME = Pattern.compile("user/\\d+/[a-f0-9]{32}\\.[a-z0-9]+");

    private final MinioProperties minioProperties;

    private final MinioClient minioClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName, Long ownerId) {
        log.info("## 上传文件至 Minio ...");

        // 文件的原始名称
        String originalFileName = file.getOriginalFilename();
        // 文件的 Content-Type
        String contentType = file.getContentType();

        String key = IdUtil.fastSimpleUUID();
        String suffix = "." + FileNameUtil.extName(originalFileName).toLowerCase(Locale.ROOT);
        String objectName = String.format("user/%d/%s%s", ownerId, key, suffix);

        log.info("==> 开始上传文件至 Minio, ObjectName: {}", objectName);

        // 上传文件至 Minio
        try (java.io.InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        }

        // 返回文件的访问链接
        String url = String.format("%s/%s/%s", minioProperties.getEndpoint(), bucketName, objectName);
        log.info("==> 上传文件至 Minio 成功，访问路径: {}", url);
        return url;
    }

    @Override
    @SneakyThrows
    public void deleteFile(String fileUrl, String bucketName, Long ownerId) {
        URI uri = URI.create(fileUrl);
        URI endpoint = URI.create(minioProperties.getEndpoint());
        if (!equalsEndpoint(uri, endpoint) || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("文件地址不属于当前 MinIO 服务");
        }
        String path = uri.getRawPath();
        String bucketPrefix = "/" + bucketName + "/";
        if (path == null || !path.startsWith(bucketPrefix) || path.length() == bucketPrefix.length()) {
            throw new IllegalArgumentException("文件地址不属于当前 MinIO Bucket");
        }
        String objectName = path.substring(bucketPrefix.length());
        if (!objectName.startsWith("user/" + ownerId + "/") || !OWNED_OBJECT_NAME.matcher(objectName).matches()) {
            throw new IllegalArgumentException("无权删除该文件");
        }
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
        log.info("==> MinIO 文件删除成功, ObjectName: {}", objectName);
    }

    @Override
    @SneakyThrows
    public PresignedUrlRspVO getPresignedUploadUrl(String fileName, String contentType, String bucketName, Long ownerId) {
        String key = IdUtil.fastSimpleUUID();
        String ext = FileNameUtil.extName(fileName);
        String suffix = ext == null || ext.isEmpty() ? "" : "." + ext.toLowerCase(Locale.ROOT);
        String objectName = String.format("user/%d/%s%s", ownerId, key, suffix);

        GetPresignedObjectUrlArgs.Builder builder = GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucketName)
                .object(objectName)
                .expiry(10, TimeUnit.MINUTES);
        if (StringUtils.isNotBlank(contentType)) {
            builder.extraHeaders(Map.of("Content-Type", contentType));
        }

        String uploadUrl = minioClient.getPresignedObjectUrl(builder.build());

        String downloadUrl = String.format("%s/%s/%s", minioProperties.getEndpoint(), bucketName, objectName);
        return PresignedUrlRspVO.builder()
                .uploadUrl(uploadUrl)
                .downloadUrl(downloadUrl)
                .build();
    }

    private boolean equalsEndpoint(URI actual, URI expected) {
        return actual.getScheme() != null
                && actual.getScheme().equalsIgnoreCase(expected.getScheme())
                && actual.getHost() != null
                && actual.getHost().equalsIgnoreCase(expected.getHost())
                && actual.getPort() == expected.getPort();
    }
}
