package hk.ljx.fishhub.user.api;

import hk.ljx.fishhub.user.dto.req.FindUserByPhoneReqDTO;
import hk.ljx.fishhub.user.dto.req.RegisterUserReqDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByPhoneRspDTO;
import hk.ljx.framework.common.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static hk.ljx.fishhub.user.constant.ApiConstants.SERVICE_NAME;

@FeignClient(name = SERVICE_NAME)
public interface UserFeignApi {

    String PREFIX = "/user";

    @PostMapping(value = PREFIX + "/register")
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     * @param findUserByPhoneReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/findByPhone")
    Response<FindUserByPhoneRspDTO> findByPhone(@RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO);

}
