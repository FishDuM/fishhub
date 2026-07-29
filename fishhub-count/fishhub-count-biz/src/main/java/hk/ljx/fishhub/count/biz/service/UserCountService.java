package hk.ljx.fishhub.count.biz.service;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdRspDTO;

public interface UserCountService {

    Response<FindUserCountByIdRspDTO> findUserCountData(FindUserCountByIdReqDTO findUserCountByIdReqDTO);
}

