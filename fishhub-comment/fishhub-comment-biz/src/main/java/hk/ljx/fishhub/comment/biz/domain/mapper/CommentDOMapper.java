package hk.ljx.fishhub.comment.biz.domain.mapper;

import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.comment.biz.model.bo.CommentHeatBO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentDOMapper {
    int deleteByPrimaryKey(Long id);

    /** 删除一级评论下，所有二级评论 */
    int deleteByParentId(Long commentId);

    /** 批量删除评论 */
    int deleteByIds(@Param("commentIds") List<Long> commentIds);

    /** 批量插入评论 */
    int batchInsert(@Param("comments") List<CommentBO> comments);

    CommentDO selectByPrimaryKey(Long id);

    /** 根据 reply_comment_id 查询 */
    CommentDO selectByReplyCommentId(Long commentId);

    List<CommentDO> selectByParentId(Long parentId);

    /** 根据评论 ID 批量查询 */
    List<CommentDO> selectByCommentIds(@Param("commentIds") List<Long> commentIds);

    List<CommentDO> selectNoteIdsByCommentIds(@Param("commentIds") List<Long> commentIds);

    /** 批量查询计数数据 */
    List<CommentDO> selectCommentCountByIds(@Param("commentIds") List<Long> commentIds);

    /** 查询子评论 */
    List<CommentDO> selectChildCommentsByParentIdAndLimit(@Param("parentId") Long parentId,
                                                          @Param("limit") int limit);

    /** 查询子评论（最新优先，用于缓存重建；与增量 trim 同向保留最新 N 条） */
    List<CommentDO> selectLatestChildCommentsByParentIdAndLimit(@Param("parentId") Long parentId,
                                                                @Param("limit") int limit);

    /** 批量查询二级评论 */
    List<CommentDO> selectTwoLevelCommentByIds(@Param("commentIds") List<Long> commentIds);

    /** 查询一级评论下最早回复的评论 */
    CommentDO selectEarliestByParentId(Long parentId);

    /** 批量查询一批一级评论下各自最早回复（level=2 且 create_time 最早） */
    List<CommentDO> selectEarliestFirstReplyByParentIds(@Param("parentIds") List<Long> parentIds);

    /** 批量回填一级评论的 first_reply_comment_id（key=一级评论ID, value=最早回复ID） */
    int batchUpdateFirstReplyCommentIds(@Param("commentFirstReplyBOS") List<hk.ljx.fishhub.comment.biz.model.bo.CommentFirstReplyBO> commentFirstReplyBOS);

    /** 查询评论分页数据 */
    List<CommentDO> selectPageList(@Param("noteId") Long noteId,
                                   @Param("offset") long offset,
                                   @Param("pageSize") long pageSize);

    Long selectOneLevelCountByNoteId(Long noteId);

    /** 查询二级评论分页数据 */
    List<CommentDO> selectChildPageList(@Param("parentId") Long parentId,
                                        @Param("offset") long offset,
                                        @Param("pageSize") long pageSize);

    /** 查询热门评论 */
    List<CommentDO> selectHeatComments(Long noteId);

    /** 查询一级评论下子评论总数 */
    Long selectChildCommentTotalById(Long commentId);

    /** 批量更新热度值 */
    int batchUpdateHeatByCommentIds(@Param("commentIds") List<Long> commentIds,
                                    @Param("commentHeatBOS") List<CommentHeatBO> commentHeatBOS);

    /** 更新一级评论的 first_reply_comment_id */
    int updateFirstReplyCommentIdByPrimaryKey(@Param("firstReplyCommentId") Long firstReplyCommentId,
                                              @Param("id") Long id);

    int updateChildCommentTotal(@Param("id") Long id, @Param("delta") long delta);

    /**
     * 更新评论点赞数（count 聚合后投递，本服务落库；t_comment 的唯一写入口）
     */
    int updateLikeTotalByCommentId(@Param("count") Integer count,
                                   @Param("commentId") Long commentId);

}
