package hk.ljx.fishhub.note.biz.service;


import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNotePageListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNoteRspVO;

public interface DiscoverService {

    PageResponse<FindDiscoverNoteRspVO> findNoteList(FindDiscoverNotePageListReqVO findDiscoverNoteListReqVO);
}

