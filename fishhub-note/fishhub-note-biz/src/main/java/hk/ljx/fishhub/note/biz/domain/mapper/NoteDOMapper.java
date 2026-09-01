package hk.ljx.fishhub.note.biz.domain.mapper;

import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NoteDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteDO record);

    int insertSelective(NoteDO record);

    NoteDO selectByPrimaryKey(Long id);

    int selectCountByNoteId(Long noteId);

    /**
     * 查询笔记的发布者用户 ID
     * @param noteId
     * @return
     */
    Long selectCreatorIdByNoteId(Long noteId);

    /**
     * 查询笔记访问鉴权所需的最小字段。
     *
     * @param noteId 笔记 ID
     * @return 笔记创建者及可见性；笔记不存在时返回 {@code null}
     */
    NoteDO selectAccessInfoByNoteId(Long noteId);

    /**
     * 异步互动消费时读取笔记状态和发布者；查询范围包含逻辑删除记录，便于取消操作清理旧关系。
     */
    NoteDO selectInteractionInfoByNoteId(Long noteId);

    /**
     * 批量读取互动消费所需的笔记状态与发布者（含逻辑删除）
     */
    List<NoteDO> selectInteractionInfosByNoteIds(@Param("noteIds") List<Long> noteIds);

    List<NoteDO> selectAccessInfosByNoteIds(@Param("noteIds") List<Long> noteIds);

    /**
     * 查询个人主页已发布笔记列表
     * @param creatorId
     * @param cursor
     * @return
     */
    List<NoteDO> selectPublishedNoteListByUserIdAndCursor(@Param("creatorId") Long creatorId,
                                                          @Param("cursor") Long cursor,
                                                          @Param("includePrivate") boolean includePrivate);

    List<NoteDO> selectCollectedNoteListByUserIdAndCursor(@Param("userId") Long userId,
                                                          @Param("cursorTime") LocalDateTime cursorTime,
                                                          @Param("cursorId") Long cursorId);

    List<NoteDO> selectLikedNoteListByUserIdAndCursor(@Param("userId") Long userId,
                                                      @Param("cursorTime") LocalDateTime cursorTime,
                                                      @Param("cursorId") Long cursorId);

    List<NoteDO> selectDiscoverPageListByCursor(@Param("channelId") Long channelId,
                                                  @Param("cursor") Long cursor,
                                                  @Param("limit") long limit);


    int updateByPrimaryKeySelective(NoteDO record);

    int updateByPrimaryKey(NoteDO record);

    int logicalDeleteByPrimaryKey(NoteDO record);

    int updateVisibility(NoteDO noteDO);

    int updateIsTop(NoteDO noteDO);

    /**
     * 原子更新点赞数
     */
    int updateLikeCount(@Param("noteId") Long noteId, @Param("delta") Integer delta);

    /**
     * 原子更新收藏数
     */
    int updateCollectCount(@Param("noteId") Long noteId, @Param("delta") Integer delta);

    /**
     * 原子更新评论数
     */
    int updateCommentCount(@Param("noteId") Long noteId, @Param("delta") Integer delta);
}
