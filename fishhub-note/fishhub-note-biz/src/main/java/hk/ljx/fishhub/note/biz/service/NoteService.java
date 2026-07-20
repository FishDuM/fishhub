package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.model.vo.FindNoteDetailReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindNoteDetailRspVO;
import hk.ljx.fishhub.note.biz.model.vo.PublishNoteReqVO;
import hk.ljx.framework.common.response.Response;

public interface NoteService {

    /**
     * 笔记发布
     * @param publishNoteReqVO
     * @return
     */
    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

    /**
     * 笔记详情
     * @param findNoteDetailReqVO
     * @return
     */
    Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO);
}
