package hk.ljx.fishhub.auth.service;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.auth.model.vo.user.UpdatePasswordReqVO;
import hk.ljx.fishhub.auth.model.vo.user.UserLoginReqVO;


public interface AuthService {

    /**
     * 登录与注册
     * @param userLoginReqVO
     * @return
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);

    /**
     * 退出登录
     * @return
     */
    Response<?> logout();

    /**
     * 修改密码
     * @param updatePasswordReqVO
     * @return
     */
    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);
}
