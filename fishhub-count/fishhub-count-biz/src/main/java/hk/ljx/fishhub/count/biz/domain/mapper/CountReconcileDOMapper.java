package hk.ljx.fishhub.count.biz.domain.mapper;

import hk.ljx.fishhub.count.biz.domain.dataobject.CommentCountReconcileBO;
import hk.ljx.fishhub.count.biz.domain.dataobject.IdCountBO;
import hk.ljx.fishhub.count.biz.domain.dataobject.NoteCountDO;
import hk.ljx.fishhub.count.biz.domain.dataobject.UserCountDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CountReconcileDOMapper {

    // ==================== 笔记对账 ====================

    /**
     * 游标查询下一批已发布的笔记 ID
     */
    List<Long> selectNextNoteIds(@Param("lastId") Long lastId, @Param("limit") int limit);

    /**
     * 批量统计笔记点赞数
     */
    List<IdCountBO> countNoteLikes(@Param("noteIds") List<Long> noteIds);

    /**
     * 批量统计笔记收藏数
     */
    List<IdCountBO> countNoteCollections(@Param("noteIds") List<Long> noteIds);

    /**
     * 批量统计笔记一级评论数
     */
    List<IdCountBO> countNoteComments(@Param("noteIds") List<Long> noteIds);

    /**
     * 批量写入或更新笔记计数
     */
    int batchUpsertNoteCounts(@Param("list") List<NoteCountDO> list);

    // ==================== 用户对账 ====================

    /**
     * 游标查询下一批有效用户 ID
     */
    List<Long> selectNextUserIds(@Param("lastId") Long lastId, @Param("limit") int limit);

    /**
     * 批量统计用户粉丝数
     */
    List<IdCountBO> countUserFans(@Param("userIds") List<Long> userIds);

    /**
     * 批量统计用户关注数
     */
    List<IdCountBO> countUserFollowings(@Param("userIds") List<Long> userIds);

    /**
     * 批量统计用户发布笔记数
     */
    List<IdCountBO> countUserNotes(@Param("userIds") List<Long> userIds);

    /**
     * 批量统计用户获得点赞数
     */
    List<IdCountBO> countUserLikes(@Param("userIds") List<Long> userIds);

    /**
     * 批量统计用户获得收藏数
     */
    List<IdCountBO> countUserCollections(@Param("userIds") List<Long> userIds);

    /**
     * 批量写入或更新用户计数
     */
    int batchUpsertUserCounts(@Param("list") List<UserCountDO> list);

    // ==================== 评论对账 ====================

    /**
     * 游标查询下一批评论 ID
     */
    List<Long> selectNextCommentIds(@Param("lastId") Long lastId, @Param("limit") int limit);

    /**
     * 批量统计评论点赞数
     */
    List<IdCountBO> countCommentLikes(@Param("commentIds") List<Long> commentIds);

    /**
     * 批量统计一级评论的二级子评论数
     */
    List<IdCountBO> countChildComments(@Param("parentIds") List<Long> parentIds);

    /**
     * 批量更新评论点赞数与子评论数
     */
    int batchUpdateCommentCounts(@Param("list") List<CommentCountReconcileBO> list);
}
