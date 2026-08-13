package hk.ljx.fishhub.note.biz.rpc;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.oss.api.FileFeignApi;
import hk.ljx.fishhub.oss.dto.DeleteFileReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@Slf4j
public class OssRpcService {

    @Resource
    private FileFeignApi fileFeignApi;

    public void deleteFiles(Collection<String> fileUrls) {
        fileUrls.forEach(fileUrl -> {
            try {
                DeleteFileReqDTO request = new DeleteFileReqDTO();
                request.setFileUrl(fileUrl);
                Response<?> response = fileFeignApi.deleteFile(request);
                if (response == null || !response.isSuccess()) {
                    log.error("对象存储文件删除失败, fileUrl={}, response={}", fileUrl, response);
                }
            } catch (Exception e) {
                log.error("对象存储文件删除失败, fileUrl={}", fileUrl, e);
            }
        });
    }
}
