package hk.ljx.fishhub.note.biz.domain.mapper;

import hk.ljx.fishhub.note.biz.domain.dataobject.TopicDO;

import java.util.List;

public interface TopicDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(TopicDO record);

    int insertSelective(TopicDO record);

    TopicDO selectByPrimaryKey(Long id);

    String selectNameByPrimaryKey(Long id);

    List<TopicDO> selectByLikeName(String keyword);

    List<TopicDO> selectAllEnabled();

    int updateByPrimaryKeySelective(TopicDO record);

    int updateByPrimaryKey(TopicDO record);
}
