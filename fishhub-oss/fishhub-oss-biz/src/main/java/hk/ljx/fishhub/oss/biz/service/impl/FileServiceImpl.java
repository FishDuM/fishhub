package hk.ljx.fishhub.oss.biz.service.impl;

import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.oss.biz.model.vo.PresignedUrlReqVO;
import hk.ljx.fishhub.oss.biz.model.vo.PresignedUrlRspVO;
import hk.ljx.fishhub.oss.biz.service.FileService;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import hk.ljx.fishhub.oss.dto.DeleteFileReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;


@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileStrategy fileStrategy;

    private static final String BUCKET_NAME = "fishhub";
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "video/mp4", "video/webm", "video/quicktime");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "mov");

    @Override
    public Response<?> uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("上传文件不能超过 100MB");
        }
        validateAndGetExtension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持图片和常见视频格式");
        }
        // 上传文件
        String url = fileStrategy.uploadFile(file, BUCKET_NAME, requireCurrentUserId());

        return Response.success(url);
    }

    @Override
    public Response<?> deleteFile(DeleteFileReqDTO request) {
        fileStrategy.deleteFile(request.getFileUrl(), BUCKET_NAME, requireCurrentUserId());
        return Response.success();
    }

    @Override
    public Response<PresignedUrlRspVO> getPresignedUrl(PresignedUrlReqVO request) {
        String fileName = request.getFileName();
        validateAndGetExtension(fileName);

        String contentType = request.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持图片和常见视频格式");
        }

        PresignedUrlRspVO rsp = fileStrategy.getPresignedUploadUrl(
                fileName, request.getContentType(), BUCKET_NAME, requireCurrentUserId());
        return Response.success(rsp);
    }

    private String validateAndGetExtension(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("文件名缺少合法扩展名");
        }
        String lowerExt = extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(lowerExt)) {
            throw new IllegalArgumentException("仅支持图片和常见视频格式");
        }
        return lowerExt;
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("缺少文件所属用户上下文");
        }
        return userId;
    }
}
