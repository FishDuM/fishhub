package hk.ljx.fishhub.search.biz.controller;

import hk.ljx.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.dto.RebuildNoteDocumentReqDTO;
import hk.ljx.fishhub.search.biz.model.vo.SearchNoteReqVO;
import hk.ljx.fishhub.search.biz.model.vo.SearchNoteRspVO;
import hk.ljx.fishhub.search.biz.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/search")
@Slf4j
public class NoteController {

    @Resource
    private NoteService noteService;

    @PostMapping("/note")
    @ApiOperationLog(description = "搜索笔记")
    public PageResponse<SearchNoteRspVO> searchNote(@RequestBody @Validated SearchNoteReqVO searchNoteReqVO) {
        return noteService.searchNote(searchNoteReqVO);
    }

    // ===================================== 对其他服务提供的接口 =====================================
    @PostMapping("/note/document/rebuild")
    @ApiOperationLog(description = "用户文档重建")
    public Response<Long> rebuildDocument(@Validated @RequestBody RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO) {
        return noteService.rebuildDocument(rebuildNoteDocumentReqDTO);
    }

}
