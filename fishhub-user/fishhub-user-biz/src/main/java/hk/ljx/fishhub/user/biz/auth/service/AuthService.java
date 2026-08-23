package hk.ljx.fishhub.user.biz.auth.service;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.auth.model.vo.captcha.CaptchaRspVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UpdatePasswordReqVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserRegisterReqVO;

public interface AuthService {

    /**
     * 获取图形验证码
     *
     * @return
     */
    Response<CaptchaRspVO> getCaptcha();

    /**
     * 用户注册
     *
     * @param userRegisterReqVO
     * @return Sa-Token token 值
     */
    Response<String> register(UserRegisterReqVO userRegisterReqVO);

    /**
     * 用户登录
     *
     * @param userLoginReqVO
     * @return Sa-Token token 值
     */
    Response<String> login(UserLoginReqVO userLoginReqVO);

    /**
     * 退出登录
     *
     * @return
     */
    Response<?> logout();

    /**
     * 修改密码
     *
     * @param updatePasswordReqVO
     * @return
     */
    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);
}
