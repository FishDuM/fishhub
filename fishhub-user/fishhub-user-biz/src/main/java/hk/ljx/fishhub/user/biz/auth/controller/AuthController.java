package hk.ljx.fishhub.user.biz.auth.controller;

import hk.ljx.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.auth.model.vo.captcha.CaptchaRspVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UpdatePasswordReqVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserRegisterReqVO;
import hk.ljx.fishhub.user.biz.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证接口
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/captcha")
    @ApiOperationLog(description = "获取图形验证码")
    public Response<CaptchaRspVO> getCaptcha() {
        return authService.getCaptcha();
    }

    @PostMapping("/register")
    @ApiOperationLog(description = "用户注册")
    public Response<String> register(@Validated @RequestBody UserRegisterReqVO userRegisterReqVO) {
        return authService.register(userRegisterReqVO);
    }

    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录")
    public Response<String> login(@Validated @RequestBody UserLoginReqVO userLoginReqVO) {
        return authService.login(userLoginReqVO);
    }

    @PostMapping("/logout")
    @ApiOperationLog(description = "账号登出")
    public Response<?> logout() {
        return authService.logout();
    }

    @PostMapping("/password/update")
    @ApiOperationLog(description = "修改密码")
    public Response<?> updatePassword(@Validated @RequestBody UpdatePasswordReqVO updatePasswordReqVO) {
        return authService.updatePassword(updatePasswordReqVO);
    }
}
