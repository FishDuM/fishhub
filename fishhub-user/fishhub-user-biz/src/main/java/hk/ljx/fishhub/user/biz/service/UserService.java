package hk.ljx.fishhub.user.biz.service;

import hk.ljx.fishhub.user.biz.model.vo.UpdateUserInfoReqVO;
import hk.ljx.framework.common.response.Response;

public interface UserService {

    /**
     * 更新用户信息
     * @param updateUserInfoReqVO
     * @return
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);
}
