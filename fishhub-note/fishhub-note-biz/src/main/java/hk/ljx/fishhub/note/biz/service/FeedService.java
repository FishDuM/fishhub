package hk.ljx.fishhub.note.biz.service;

import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.biz.model.vo.FindChannelRspVO;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNoteListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicRspVO;
import hk.ljx.fishhub.note.biz.model.vo.NoteItemRspVO;

import java.util.List;

public interface FeedService {
    Response<List<FindChannelRspVO>> findChannelList();
    PageResponse<NoteItemRspVO> findDiscoverNoteList(FindDiscoverNoteListReqVO request);
    Response<List<FindTopicRspVO>> findTopicList(FindTopicListReqVO request);
}
