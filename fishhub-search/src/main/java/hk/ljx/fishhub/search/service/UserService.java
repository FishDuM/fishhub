package hk.ljx.fishhub.search.service;

import hk.ljx.fishhub.search.dto.req.RebuildUserDocumentReqDTO;
import hk.ljx.fishhub.search.model.vo.SearchUserReqVO;
import hk.ljx.fishhub.search.model.vo.SearchUserRspVO;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;

public interface UserService {

    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);

    /** 重建指定用户的 Elasticsearch 文档 */
    Response<Long> rebuildDocument(RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);
}
