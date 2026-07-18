package hk.ljx.fishhub.auth.service;

import hk.ljx.fishhub.auth.modal.vo.user.UpdatePasswordReqVO;
import hk.ljx.fishhub.auth.modal.vo.user.UserLoginReqVO;
import hk.ljx.framework.common.response.Response;

public interface UserService {

    /**
     * 登录与注册
     *
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
