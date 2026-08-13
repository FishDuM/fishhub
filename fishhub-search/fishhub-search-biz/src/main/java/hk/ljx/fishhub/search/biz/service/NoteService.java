package hk.ljx.fishhub.search.biz.service;

import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.search.dto.req.RebuildNoteDocumentReqDTO;
import hk.ljx.fishhub.search.biz.model.vo.SearchNoteReqVO;
import hk.ljx.fishhub.search.biz.model.vo.SearchNoteRspVO;


public interface NoteService {

    /**
     * 搜索笔记
     * @param searchNoteReqVO
     * @return
     */
    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);

    /**
     * 重建笔记文档
     * @param rebuildNoteDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);
}
