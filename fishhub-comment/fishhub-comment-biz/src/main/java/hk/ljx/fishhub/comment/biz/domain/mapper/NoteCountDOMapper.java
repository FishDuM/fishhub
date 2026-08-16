package hk.ljx.fishhub.comment.biz.domain.mapper;

import hk.ljx.fishhub.comment.biz.domain.dataobject.NoteCountDO;
import org.apache.ibatis.annotations.Param;

public interface NoteCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCountDO record);

    int insertSelective(NoteCountDO record);

    NoteCountDO selectByPrimaryKey(Long id);

    /**
     * 查询笔记评论总数
     * @param noteId
     * @return
     */
    Long selectCommentTotalByNoteId(Long noteId);

    int updateByPrimaryKeySelective(NoteCountDO record);

    int updateByPrimaryKey(NoteCountDO record);

    /**
     * 累加评论总数，行不存在时按增量建行
     * @param noteId
     * @param count
     * @return
     */
    int insertOrUpdateCommentTotalByNoteId(@Param("noteId") Long noteId,
                                           @Param("count") int count);
}