package hk.ljx.fishhub.auth.controller;

import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @ApiOperationLog(description = "接口测试")
    @GetMapping("/test")
    public Response<String> test(){
        return Response.success("test");
    }

    @PostMapping("/test2")
    @ApiOperationLog(description = "测试接口2")
    public Response<User> test2(@RequestBody @Validated User user) {
        int i = 1 / 0;
        return Response.success(user);
    }
}
