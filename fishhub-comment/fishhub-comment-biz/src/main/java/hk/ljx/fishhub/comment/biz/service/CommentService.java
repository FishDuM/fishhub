package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.comment.biz.model.vo.*;

import java.util.List;

public interface CommentService {

    /** 发布评论 */
    Response<?> publishComment(PublishCommentReqVO publishCommentReqVO);

    /** 一级评论列表分页查询 */
    PageResponse<FindCommentItemRspVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO);

    /** 二级评论分页查询 */
    PageResponse<FindChildCommentItemRspVO> findChildCommentPageList(FindChildCommentPageListReqVO findChildCommentPageListReqVO);

    /** 评论点赞 */
    Response<?> likeComment(LikeCommentReqVO likeCommentReqVO);

    /** 取消评论点赞 */
    Response<?> unlikeComment(UnlikeCommentReqVO unlikeCommentReqVO);

    /** 批量查询已点赞评论 ID */
    Response<List<Long>> findLikedCommentIds(FindLikedCommentIdsReqVO reqVO);

    /** 我的点赞足迹分页 */
    PageResponse<FindLikedCommentItemRspVO> findLikedCommentPage(FindLikedCommentPageReqVO reqVO);

    /** 删除评论 */
    Response<?> deleteComment(DeleteCommentReqVO deleteCommentReqVO);
}
