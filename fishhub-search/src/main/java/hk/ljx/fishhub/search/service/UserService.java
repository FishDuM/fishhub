package hk.ljx.fishhub.search.service;

import hk.ljx.fishhub.search.model.vo.SearchUserReqVO;
import hk.ljx.fishhub.search.model.vo.SearchUserRspVO;
import hk.ljx.framework.common.response.PageResponse;

public interface UserService {

    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);
}