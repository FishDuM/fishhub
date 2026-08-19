package hk.ljx.fishhub.note.biz.controller;

import hk.ljx.fishhub.note.biz.model.vo.DiscoverNotePageResponse;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNoteListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.NoteItemRspVO;
import hk.ljx.fishhub.note.biz.service.FeedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/discover")
@RequiredArgsConstructor
public class DiscoverController {
    private final FeedService feedService;

    @PostMapping("/note/list")
    public DiscoverNotePageResponse<NoteItemRspVO> findDiscoverNoteList(@Valid @RequestBody FindDiscoverNoteListReqVO request) {
        return feedService.findDiscoverNoteList(request);
    }
}
