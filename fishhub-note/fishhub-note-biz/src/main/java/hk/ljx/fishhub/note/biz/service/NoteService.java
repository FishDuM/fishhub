package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.model.vo.FindNoteDetailReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindNoteDetailRspVO;
import hk.ljx.fishhub.note.biz.model.vo.PublishNoteReqVO;
import hk.ljx.fishhub.note.biz.model.vo.UpdateNoteReqVO;
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

    /**
     * 笔记更新
     * @param updateNoteReqVO
     * @return
     */
    Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO);

    /**
     * 删除本地笔记缓存
     * @param noteId
     */
    void deleteNoteLocalCache(Long noteId);
}
