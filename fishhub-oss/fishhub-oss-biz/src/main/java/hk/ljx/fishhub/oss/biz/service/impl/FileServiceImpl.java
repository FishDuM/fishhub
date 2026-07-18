package hk.ljx.fishhub.oss.biz.service.impl;

import hk.ljx.fishhub.oss.biz.service.FileService;
import hk.ljx.fishhub.oss.biz.strategy.FileStrategy;
import hk.ljx.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Resource
    private FileStrategy fileStrategy;

    @Override
    public Response<?> uploadFile(MultipartFile file) {
        fileStrategy.uploadFile(file, "fishhub");
        return Response.success();
    }
}
