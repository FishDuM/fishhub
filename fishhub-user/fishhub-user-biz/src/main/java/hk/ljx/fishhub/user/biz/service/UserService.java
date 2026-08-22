package hk.ljx.fishhub.user.biz.service;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.model.vo.FindUserProfileReqVO;
import hk.ljx.fishhub.user.biz.model.vo.FindUserProfileRspVO;
import hk.ljx.fishhub.user.biz.model.vo.UpdateUserInfoReqVO;
import hk.ljx.fishhub.user.dto.req.*;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.resp.ResolveLoginableUserRspDTO;

import java.util.List;


public interface UserService {
    void deleteUserLocalCache(Long userId);

    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);

    /**
     * 查询手机号对应的可登录账号；不存在时创建默认账号。
     *
     * @param request
     * @return
     */
    Response<ResolveLoginableUserRspDTO> resolveOrRegisterLoginableUser(ResolveLoginableUserReqDTO request);

    /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return
     */
    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /**
     * 更新密码
     *
     * @param updateUserPasswordReqDTO
     * @return
     */
    Response<Boolean> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param findUserByIdReqDTO
     * @return
     */
    Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO);

    /**
     * 查询未禁用、未删除的用户。
     */
    Response<FindUserByIdRspDTO> findActiveById(FindUserByIdReqDTO findUserByIdReqDTO);

    /**
     * 批量根据用户 ID 查询用户信息
     *
     * @param findUsersByIdsReqDTO
     * @return
     */
    Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO);

    /**
     * 获取用户主页信息
     *
     * @return
     */
    Response<FindUserProfileRspVO> findUserProfile(FindUserProfileReqVO findUserProfileReqVO);

}
