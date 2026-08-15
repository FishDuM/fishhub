package hk.ljx.fishhub.search.biz.service;

import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.fishhub.search.biz.model.vo.SearchUserReqVO;
import hk.ljx.fishhub.search.biz.model.vo.SearchUserRspVO;


public interface UserService {

    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);

}
