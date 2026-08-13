package hk.ljx.fishhub.oss.biz.controller;

import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.oss.biz.service.FileService;
import hk.ljx.fishhub.oss.dto.DeleteFileReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;


@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file) {
        log.info("当前用户 ID: {}", LoginUserContextHolder.getUserId());
        return fileService.uploadFile(file);
    }

    @PostMapping("/delete")
    public Response<?> deleteFile(@Validated @RequestBody DeleteFileReqDTO request) {
        return fileService.deleteFile(request);
    }

}
