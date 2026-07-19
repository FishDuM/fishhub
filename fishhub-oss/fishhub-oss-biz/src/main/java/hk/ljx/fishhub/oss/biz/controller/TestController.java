package hk.ljx.fishhub.oss.biz.controller;

import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/file")
@Slf4j
public class TestController {

    @PostMapping("/test")
    @ApiOperationLog(description = "Feign测试")
    public Response<?> test(){
        log.info("test");
        return Response.success();
    }
}
