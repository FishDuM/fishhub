package hk.ljx.fishhub.search.service;

import hk.ljx.fishhub.search.dto.req.RebuildNoteDocumentReqDTO;
import hk.ljx.fishhub.search.model.vo.SearchNoteReqVO;
import hk.ljx.fishhub.search.model.vo.SearchNoteRspVO;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;

public interface NoteService {

    /**
     * 搜索笔记
     * @param searchNoteReqVO
     * @return
     */
    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);

    /** 重建指定笔记的 Elasticsearch 文档 */
    Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);
}
