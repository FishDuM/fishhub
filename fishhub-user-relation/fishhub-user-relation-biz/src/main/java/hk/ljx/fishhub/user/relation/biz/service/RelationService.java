package hk.ljx.fishhub.user.relation.biz.service;

import hk.ljx.fishhub.user.relation.biz.model.vo.FollowUserReqVO;
import hk.ljx.framework.common.response.Response;

public interface RelationService {

    /**
     * 关注用户
     * @param followUserReqVO
     * @return
     */
    Response<?> follow(FollowUserReqVO followUserReqVO);

}