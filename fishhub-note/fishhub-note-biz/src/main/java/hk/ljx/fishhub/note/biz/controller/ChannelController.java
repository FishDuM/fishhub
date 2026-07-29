package hk.ljx.fishhub.note.biz.controller;

import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.biz.model.vo.*;
import hk.ljx.fishhub.note.biz.service.ChannelService;
import hk.ljx.fishhub.note.biz.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/channel")
@Slf4j
public class ChannelController {

    @Resource
    private ChannelService channelService;

    @PostMapping(value = "/list")
    @ApiOperationLog(description = "获取所有频道")
    public Response<List<FindChannelRspVO>> findChannelList() {
        return channelService.findChannelList();
    }

}

