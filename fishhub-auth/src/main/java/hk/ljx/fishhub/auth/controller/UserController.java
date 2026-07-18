package hk.ljx.fishhub.auth.controller;

import hk.ljx.fishhub.auth.modal.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.auth.service.UserService;
import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(@Validated @RequestBody UserLoginReqVO userLoginReqVO) {
        return userService.loginAndRegister(userLoginReqVO);
    }

    @PostMapping("/logout")
    @ApiOperationLog(description = "账号登出")
    public Response<?> logout() {
        // todo 账号退出登录逻辑待实现
        return Response.success();
    }
}