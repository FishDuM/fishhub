package hk.ljx.fishhub.user.biz.rpc;

import hk.ljx.fishhub.oss.api.FileFeignApi;
import hk.ljx.framework.common.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class OssRpcService {

    @Resource
    private FileFeignApi fileFeignApi;

    public String uploadFile(MultipartFile file) {
        // 调用文件服务上传文件
        Response<?> response = fileFeignApi.uploadFile(file);
        if (!response.isSuccess()){
            return null;
        }
        return (String) response.getData();
    }
}
