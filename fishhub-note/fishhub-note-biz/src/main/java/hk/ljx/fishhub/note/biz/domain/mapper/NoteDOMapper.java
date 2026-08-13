package hk.ljx.fishhub.note.biz.domain.mapper;

import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import org.apache.ibatis.annotations.Param;

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
                                                          @Param("cursor") Long cursor);

    List<NoteDO> selectLikedNoteListByUserIdAndCursor(@Param("userId") Long userId,
                                                      @Param("cursor") Long cursor);

    long selectDiscoverTotalCount(@Param("channelId") Long channelId);

    List<NoteDO> selectDiscoverPageList(@Param("channelId") Long channelId,
                                        @Param("offset") long offset,
                                        @Param("limit") long limit);


    int updateByPrimaryKeySelective(NoteDO record);

    int updateByPrimaryKey(NoteDO record);

    /**
     * 按聚合版本更新完整笔记快照，防止并发编辑覆盖。
     */
    int updateByPrimaryKeyAndRevision(NoteDO record);

    int logicalDeleteByPrimaryKeyAndRevision(NoteDO record);

    int updateVisibility(NoteDO noteDO);

    int updateIsTop(NoteDO noteDO);

}
