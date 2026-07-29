package hk.ljx.fishhub.note.biz.service;

import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.fishhub.note.biz.model.vo.FindProfileNotePageListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindProfileNoteRspVO;

public interface ProfileService {

    PageResponse<FindProfileNoteRspVO> findNoteList(FindProfileNotePageListReqVO findProfileNotePageListReqVO);
}

