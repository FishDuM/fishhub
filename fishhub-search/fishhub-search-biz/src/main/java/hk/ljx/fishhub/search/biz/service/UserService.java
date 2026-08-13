package hk.ljx.fishhub.search.biz.service;

import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.search.dto.req.RebuildUserDocumentReqDTO;
import hk.ljx.fishhub.search.biz.model.vo.SearchUserReqVO;
import hk.ljx.fishhub.search.biz.model.vo.SearchUserRspVO;


public interface UserService {

    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);

    /**
     * 重建用户文档
     * @param rebuildUserDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);
}
