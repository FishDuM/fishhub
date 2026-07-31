package hk.ljx.fishhub.count.api;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.constant.ApiConstants;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdsReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.fallback.CountFeignApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;


@FeignClient(name = ApiConstants.SERVICE_NAME, fallback = CountFeignApiFallback.class)
//@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface CountFeignApi {

    String PREFIX = "/count";

    /**
     * 查询用户计数
     *
     * @param findUserCountsByIdReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/user/data")
    Response<FindUserCountsByIdRspDTO> findUserCount(@RequestBody FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);

    /**
     * 批量查询笔记计数
     *
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/notes/data")
    Response<List<FindNoteCountsByIdRspDTO>> findNotesCount(@RequestBody FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO);

}
