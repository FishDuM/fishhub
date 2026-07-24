package hk.ljx.fishhub.count.biz.domain.mapper;

import hk.ljx.fishhub.count.biz.domain.dataobject.NoteCountDO;

public interface NoteCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCountDO record);

    int insertSelective(NoteCountDO record);

    NoteCountDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCountDO record);

    int updateByPrimaryKey(NoteCountDO record);

    int insertOrUpdateLikeTotalByNoteId(@org.apache.ibatis.annotations.Param("count") Integer count, @org.apache.ibatis.annotations.Param("noteId") Long noteId);

    int insertOrUpdateCollectTotalByNoteId(@org.apache.ibatis.annotations.Param("count") Integer count, @org.apache.ibatis.annotations.Param("noteId") Long noteId);
}
