package hk.ljx.fishhub.note.biz.domain.mapper;

import hk.ljx.fishhub.note.biz.domain.dataobject.ChannelDO;

import java.util.List;

public interface ChannelDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(ChannelDO record);

    int insertSelective(ChannelDO record);

    ChannelDO selectByPrimaryKey(Long id);

    List<ChannelDO> selectAllEnabled();

    int updateByPrimaryKeySelective(ChannelDO record);

    int updateByPrimaryKey(ChannelDO record);
}
