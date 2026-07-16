package hk.ljx.fishhub.auth.controller;

import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @ApiOperationLog(description = "接口测试")
    @GetMapping("/test")
    public Response<String> test(){
        return Response.success("test");
    }
}
