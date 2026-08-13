package hk.ljx.fishhub.comment.biz.domain.mapper;

import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentLikeDO;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentLikeDOMapper {
    int deleteByCommentIds(@Param("commentIds") List<Long> commentIds);
    int deleteByPrimaryKey(Long id);

    /**
     * 批量删除点赞记录
     * @param unlikes
     * @return
     */
    int batchDelete(@Param("unlikes") List<LikeUnlikeCommentMqDTO> unlikes);

    /**
     * 批量添加点赞记录
     * @param likes
     * @return
     */
    int batchInsert(@Param("likes") List<LikeUnlikeCommentMqDTO> likes);

    int insertIfAbsent(LikeUnlikeCommentMqDTO operation);

    int deleteIfPresent(LikeUnlikeCommentMqDTO operation);

    int insert(CommentLikeDO record);

    int insertSelective(CommentLikeDO record);

    CommentLikeDO selectByPrimaryKey(Long id);

    /**
     * 查询某个评论是否被点赞
     *
     * @param userId
     * @param commentId
     * @return
     */
    int selectCountByUserIdAndCommentId(@Param("userId") Long userId,
                                        @Param("commentId") Long commentId);

    /**
     * 查询对应用户点赞的所有评论
     * @param userId
     * @return
     */
    List<CommentLikeDO> selectByUserId(@Param("userId") Long userId);

    List<Long> selectLikedCommentIds(@Param("userId") Long userId,
                                     @Param("commentIds") List<Long> commentIds);

    int updateByPrimaryKeySelective(CommentLikeDO record);

    int updateByPrimaryKey(CommentLikeDO record);
}
