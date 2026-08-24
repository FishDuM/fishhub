package hk.ljx.fishhub.user.api;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.constant.ApiConstants;
import hk.ljx.fishhub.user.dto.req.*;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.dto.rsp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.rsp.ResolveLoginableUserRspDTO;
import hk.ljx.fishhub.user.dto.rsp.UserRolePermissionRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;


@FeignClient(name = ApiConstants.SERVICE_NAME, contextId = "userFeignApi")
public interface UserFeignApi {

    String PREFIX = "/user";

    /**
     * 查询手机号对应的可登录账号；不存在时创建默认账号。
     *
     * @param request
     * @return
     */
    @PostMapping(value = PREFIX + "/resolve-loginable")
    Response<ResolveLoginableUserRspDTO> resolveOrRegisterLoginableUser(
            @RequestBody ResolveLoginableUserReqDTO request);

    /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/findByPhone")
    Response<FindUserByPhoneRspDTO> findByPhone(@RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /**
     * 更新密码
     *
     * @param updateUserPasswordReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/password/update")
    Response<Boolean> updatePassword(@RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param findUserByIdReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/findById")
    Response<FindUserByIdRspDTO> findById(@RequestBody FindUserByIdReqDTO findUserByIdReqDTO);

    /**
     * 查询未禁用、未删除的用户。
     */
    @PostMapping(value = PREFIX + "/findActiveById")
    Response<FindUserByIdRspDTO> findActiveById(@RequestBody FindUserByIdReqDTO findUserByIdReqDTO);

    /**
     * 批量查询用户信息
     *
     * @param findUsersByIdsReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/findByIds")
    Response<List<FindUserByIdRspDTO>> findByIds(@RequestBody FindUsersByIdsReqDTO findUsersByIdsReqDTO);

    /**
     * 查询用户角色与权限（登录时写入 sa-token 会话使用）
     *
     * @param request
     * @return
     */
    @PostMapping(value = PREFIX + "/findRoleAndPermissions")
    Response<UserRolePermissionRspDTO> findRoleAndPermissions(@RequestBody FindUserRolePermissionReqDTO request);
}
