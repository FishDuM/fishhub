package hk.ljx.fishhub.search.service;

import hk.ljx.fishhub.search.model.vo.SearchNoteReqVO;
import hk.ljx.fishhub.search.model.vo.SearchNoteRspVO;
import hk.ljx.framework.common.response.PageResponse;

public interface NoteService {

    /**
     * 搜索笔记
     * @param searchNoteReqVO
     * @return
     */
    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);
}