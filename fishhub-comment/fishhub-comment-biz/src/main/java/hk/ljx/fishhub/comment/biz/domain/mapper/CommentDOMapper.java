package hk.ljx.fishhub.comment.biz.domain.mapper;

import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.comment.biz.model.bo.CommentHeatBO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentDOMapper {
    int deleteByPrimaryKey(Long id);

    /**
     * 删除一级评论下，所有二级评论
     * @param commentId
     * @return
     */
    int deleteByParentId(Long commentId);

    /**
     * 批量删除评论
     * @param commentIds
     * @return
     */
    int deleteByIds(@Param("commentIds") List<Long> commentIds);

    int insert(CommentDO record);

    /**
     * 批量插入评论
     * @param comments
     * @return
     */
    int batchInsert(@Param("comments") List<CommentBO> comments);

    int insertSelective(CommentDO record);

    CommentDO selectByPrimaryKey(Long id);

    /**
     * 根据 reply_comment_id 查询
     * @param commentId
     * @return
     */
    CommentDO selectByReplyCommentId(Long commentId);

    List<CommentDO> selectByParentId(Long parentId);

    /**
     * 根据评论 ID 批量查询
     * @param commentIds
     * @return
     */
    List<CommentDO> selectByCommentIds(@Param("commentIds") List<Long> commentIds);

    List<CommentDO> selectNoteIdsByCommentIds(@Param("commentIds") List<Long> commentIds);

    /**
     * 批量查询计数数据
     * @param commentIds
     * @return
     */
    List<CommentDO> selectCommentCountByIds(@Param("commentIds") List<Long> commentIds);

    /**
     * 查询子评论
     * @param parentId
     * @param limit
     * @return
     */
    List<CommentDO> selectChildCommentsByParentIdAndLimit(@Param("parentId") Long parentId,
                                                          @Param("limit") int limit);

    /**
     * 批量查询二级评论
     * @param commentIds
     * @return
     */
    List<CommentDO> selectTwoLevelCommentByIds(@Param("commentIds") List<Long> commentIds);

    /**
     * 查询一级评论下最早回复的评论
     * @param parentId
     * @return
     */
    CommentDO selectEarliestByParentId(Long parentId);

    /**
     * 批量查询一批一级评论下各自最早回复（level=2 且 create_time 最早）
     * @param parentIds 一级评论 ID
     * @return 每条最早回复的行（parentId 即所属一级评论）
     */
    List<CommentDO> selectEarliestFirstReplyByParentIds(@Param("parentIds") List<Long> parentIds);

    /**
     * 批量回填一级评论的 first_reply_comment_id（key=一级评论ID, value=最早回复ID）
     */
    int batchUpdateFirstReplyCommentIds(@Param("commentFirstReplyBOS") List<hk.ljx.fishhub.comment.biz.model.bo.CommentFirstReplyBO> commentFirstReplyBOS);

    /**
     * 查询评论分页数据
     * @param noteId
     * @param offset
     * @param pageSize
     * @return
     */
    List<CommentDO> selectPageList(@Param("noteId") Long noteId,
                                   @Param("offset") long offset,
                                   @Param("pageSize") long pageSize);

    Long selectOneLevelCountByNoteId(Long noteId);

    /**
     * 查询二级评论分页数据
     * @param parentId
     * @param offset
     * @param pageSize
     * @return
     */
    List<CommentDO> selectChildPageList(@Param("parentId") Long parentId,
                                        @Param("offset") long offset,
                                        @Param("pageSize") long pageSize);

    /**
     * 查询热门评论
     * @param noteId
     * @return
     */
    List<CommentDO> selectHeatComments(Long noteId);

    /**
     * 查询一级评论下子评论总数
     * @param commentId
     * @return
     */
    Long selectChildCommentTotalById(Long commentId);

    int updateByPrimaryKeySelective(CommentDO record);

    int updateByPrimaryKey(CommentDO record);

    /**
     * 批量更新热度值
     * @param commentIds
     * @param commentHeatBOS
     * @return
     */
    int batchUpdateHeatByCommentIds(@Param("commentIds") List<Long> commentIds,
                                    @Param("commentHeatBOS") List<CommentHeatBO> commentHeatBOS);

    /**
     * 更新一级评论的 first_reply_comment_id
     * @param firstReplyCommentId
     * @param id
     * @return
     */
    int updateFirstReplyCommentIdByPrimaryKey(@Param("firstReplyCommentId") Long firstReplyCommentId,
                                              @Param("id") Long id);

    int updateChildCommentTotal(@Param("id") Long id, @Param("delta") long delta);

}
