package hk.ljx.fishhub.count.biz.service;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdRspDTO;

public interface NoteCountService {

    /**
     * 查询笔记计数数据
     * @param findNoteCountByIdReqDTO
     * @return
     */
    Response<FindNoteCountByIdRspDTO> findNoteCountData(FindNoteCountByIdReqDTO findNoteCountByIdReqDTO);
}

