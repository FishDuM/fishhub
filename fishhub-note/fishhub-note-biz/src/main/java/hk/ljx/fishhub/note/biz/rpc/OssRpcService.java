package hk.ljx.fishhub.note.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.oss.api.FileFeignApi;
import hk.ljx.fishhub.oss.dto.DeleteFileReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@Slf4j
@RequiredArgsConstructor
public class OssRpcService {

    private final FileFeignApi fileFeignApi;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    public void deleteFiles(Collection<String> fileUrls) {
        if (CollUtil.isEmpty(fileUrls)) {
            return;
        }
        threadPoolTaskExecutor.execute(() -> {
            for (String fileUrl : fileUrls) {
                try {
                    DeleteFileReqDTO request = new DeleteFileReqDTO();
                    request.setFileUrl(fileUrl);
                    Response<?> response = fileFeignApi.deleteFile(request);
                    if (response == null || !response.isSuccess()) {
                        log.warn("对象存储文件异步删除失败, fileUrl={}, response={}", fileUrl, response);
                    }
                } catch (Exception e) {
                    log.warn("对象存储文件异步删除异常, fileUrl={}", fileUrl, e);
                }
            }
        });
    }
}
