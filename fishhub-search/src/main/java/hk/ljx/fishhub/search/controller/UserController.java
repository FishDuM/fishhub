package hk.ljx.fishhub.search.controller;

import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.fishhub.search.dto.req.RebuildUserDocumentReqDTO;
import hk.ljx.fishhub.search.model.vo.SearchUserReqVO;
import hk.ljx.fishhub.search.model.vo.SearchUserRspVO;
import hk.ljx.fishhub.search.service.UserService;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
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
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/user")
    @ApiOperationLog(description = "搜索用户")
    public PageResponse<SearchUserRspVO> searchUser(@RequestBody @Validated SearchUserReqVO searchUserReqVO) {
        return userService.searchUser(searchUserReqVO);
    }

    @PostMapping("/user/document/rebuild")
    @ApiOperationLog(description = "重建用户搜索文档")
    public Response<Long> rebuildDocument(@RequestBody @Validated RebuildUserDocumentReqDTO request) {
        return userService.rebuildDocument(request);
    }
}
