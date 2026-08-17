package hk.ljx.fishhub.note.biz.domain.mapper;

import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteLikeDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteLikeDO record);

    int insertSelective(NoteLikeDO record);

    /**
     * 新增笔记点赞记录，若已存在，则更新笔记点赞记录
     * @param noteLikeDO
     * @return
     */
    int insertOrUpdate(NoteLikeDO noteLikeDO);

    /**
     * 批量新增/更新点赞关系（时间守卫：旧事件不覆盖新事件）
     */
    int insertOrUpdateBatch(@Param("noteLikes") List<NoteLikeDO> noteLikes);

    NoteLikeDO selectByPrimaryKey(Long id);

    int selectCountByUserIdAndNoteId(@Param("userId") Long userId, @Param("noteId") Long noteId);

    List<NoteLikeDO> selectByUserId(@Param("userId") Long userId);

    int selectNoteIsLiked(@Param("userId") Long userId, @Param("noteId") Long noteId);

    List<NoteLikeDO> selectLikedByUserIdAndLimit(@Param("userId") Long userId, @Param("limit")  int limit);

    /**
     * 查询用户，对于一批量笔记的已点赞记录
     * @param userId
     * @param noteIds
     * @return
     */
    List<NoteLikeDO> selectByUserIdAndNoteIds(@Param("userId") Long userId,
                                              @Param("noteIds") List<Long> noteIds);

    int updateByPrimaryKeySelective(NoteLikeDO record);

    int updateByPrimaryKey(NoteLikeDO record);

}