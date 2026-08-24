package hk.ljx.fishhub.user.biz.rpc;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.oss.biz.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;


@Component
@RequiredArgsConstructor
public class OssRpcService {

    private final FileService fileService;

    public String uploadFile(MultipartFile file) {
        // 本地调用文件存储服务上传文件
        Response<?> response = fileService.uploadFile(file);

        if (!response.isSuccess()) {
            return null;
        }

        // 返回图片访问链接
        return (String) response.getData();
    }
}
