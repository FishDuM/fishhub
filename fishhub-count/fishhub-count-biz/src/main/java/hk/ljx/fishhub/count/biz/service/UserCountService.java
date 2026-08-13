package hk.ljx.fishhub.count.biz.service;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdsReqDTO;

import java.util.List;


public interface UserCountService {

    /**
     * 查询用户相关计数
     * @param findUserCountsByIdReqDTO
     * @return
     */
    Response<FindUserCountsByIdRspDTO> findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);

    Response<List<FindUserCountsByIdRspDTO>> findUsersCountData(FindUserCountsByIdsReqDTO findUserCountsByIdsReqDTO);
}
