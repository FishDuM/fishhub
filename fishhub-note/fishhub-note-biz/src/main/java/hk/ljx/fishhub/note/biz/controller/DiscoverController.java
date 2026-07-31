package hk.ljx.fishhub.note.biz.controller;

import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNoteListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.NoteItemRspVO;
import hk.ljx.fishhub.note.biz.service.FeedService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/discover")
public class DiscoverController {
    @Resource
    private FeedService feedService;

    @PostMapping("/note/list")
    public PageResponse<NoteItemRspVO> findDiscoverNoteList(@Valid @RequestBody FindDiscoverNoteListReqVO request) {
        return feedService.findDiscoverNoteList(request);
    }
}
