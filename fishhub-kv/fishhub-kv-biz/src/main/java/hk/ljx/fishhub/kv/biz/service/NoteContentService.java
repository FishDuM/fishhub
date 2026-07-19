package hk.ljx.fishhub.kv.biz.service;

import hk.ljx.fishhub.kv.dto.req.AddNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.dto.req.FindNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.dto.rsp.FindNoteContentRspDTO;
import hk.ljx.framework.common.response.Response;

public interface NoteContentService {

    /**
     * 添加笔记内容
     *
     * @param addNoteContentReqDTO
     * @return
     */
    Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);

    /**
     * 查询笔记内容
     *
     * @param findNoteContentReqDTO
     * @return
     */
    Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO);
}