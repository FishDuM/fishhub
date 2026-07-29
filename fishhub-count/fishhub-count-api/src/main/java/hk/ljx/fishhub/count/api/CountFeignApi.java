package hk.ljx.fishhub.count.api;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.constant.ApiConstants;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface CountFeignApi {

    String PREFIX = "/count";

    /**
     * 查询笔记计数
     *
     * @param findNoteCountByIdReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/note/data")
    Response<FindNoteCountByIdRspDTO> findNoteCount(@RequestBody FindNoteCountByIdReqDTO findNoteCountByIdReqDTO);

    /**
     * 查询笔记计数
     *
     * @param findUserCountByIdReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/user/data")
    Response<FindUserCountByIdRspDTO> findUserCount(@RequestBody FindUserCountByIdReqDTO findUserCountByIdReqDTO);

}

