package hk.ljx.fishhub.kv.biz.service;

import hk.ljx.fishhub.kv.dto.req.AddNoteContentReqDTO;
import hk.ljx.framework.common.response.Response;

public interface NoteContentService {

    /**
     * 添加笔记内容
     *
     * @param addNoteContentReqDTO
     * @return
     */
    Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);

}