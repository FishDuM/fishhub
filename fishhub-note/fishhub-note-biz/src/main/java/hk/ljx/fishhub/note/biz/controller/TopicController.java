package hk.ljx.fishhub.note.biz.controller;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicRspVO;
import hk.ljx.fishhub.note.biz.service.FeedService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/topic")
public class TopicController {
    @Resource
    private FeedService feedService;

    @PostMapping("/list")
    public Response<List<FindTopicRspVO>> findTopicList(@Valid @RequestBody FindTopicListReqVO request) {
        return feedService.findTopicList(request);
    }
}
