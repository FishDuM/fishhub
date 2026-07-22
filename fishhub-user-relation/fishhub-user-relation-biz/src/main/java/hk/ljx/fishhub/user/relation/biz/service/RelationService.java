package hk.ljx.fishhub.user.relation.biz.service;

import hk.ljx.fishhub.user.relation.biz.model.vo.FindFollowingListReqVO;
import hk.ljx.fishhub.user.relation.biz.model.vo.FindFollowingUserRspVO;
import hk.ljx.fishhub.user.relation.biz.model.vo.FollowUserReqVO;
import hk.ljx.fishhub.user.relation.biz.model.vo.UnfollowUserReqVO;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;

public interface RelationService {

    /**
     * 关注用户
     * @param followUserReqVO
     * @return
     */
    Response<?> follow(FollowUserReqVO followUserReqVO);

    /**
     * 取关用户
     * @param unfollowUserReqVO
     * @return
     */
    Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO);

    /**
     * 查询关注列表
     * @param findFollowingListReqVO
     * @return
     */
    PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO);

}
