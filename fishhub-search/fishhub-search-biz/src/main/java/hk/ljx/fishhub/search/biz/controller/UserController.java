package hk.ljx.fishhub.search.biz.controller;

import hk.ljx.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.fishhub.search.biz.model.vo.SearchUserReqVO;
import hk.ljx.fishhub.search.biz.model.vo.SearchUserRspVO;
import hk.ljx.fishhub.search.biz.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/search")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/user")
    @ApiOperationLog(description = "搜索用户")
    public PageResponse<SearchUserRspVO> searchUser(@RequestBody @Validated SearchUserReqVO searchUserReqVO) {
        return userService.searchUser(searchUserReqVO);
    }

}
