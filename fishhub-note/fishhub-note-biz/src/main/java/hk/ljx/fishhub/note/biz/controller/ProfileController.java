package hk.ljx.fishhub.note.biz.controller;

import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.fishhub.note.biz.model.vo.FindProfileNotePageListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindProfileNoteRspVO;
import hk.ljx.fishhub.note.biz.service.ProfileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
@Slf4j
public class ProfileController {

    @Resource
    private ProfileService profileService;

    @PostMapping(value = "/note/list")
    @ApiOperationLog(description = "个人主页-查询笔记列表")
    public PageResponse<FindProfileNoteRspVO> findNoteList(@Validated @RequestBody FindProfileNotePageListReqVO findProfileNotePageListReqVO) {
        return profileService.findNoteList(findProfileNotePageListReqVO);
    }

}

