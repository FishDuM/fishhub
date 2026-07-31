package hk.ljx.fishhub.note.biz.controller;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.biz.model.vo.FindChannelRspVO;
import hk.ljx.fishhub.note.biz.service.FeedService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/channel")
public class ChannelController {
    @Resource
    private FeedService feedService;

    @PostMapping("/list")
    public Response<List<FindChannelRspVO>> findChannelList() {
        return feedService.findChannelList();
    }
}
