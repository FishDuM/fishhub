package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.model.vo.PublishNoteReqVO;
import hk.ljx.framework.common.response.Response;

public interface NoteService {

    /**
     * 笔记发布
     * @param publishNoteReqVO
     * @return
     */
    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

}