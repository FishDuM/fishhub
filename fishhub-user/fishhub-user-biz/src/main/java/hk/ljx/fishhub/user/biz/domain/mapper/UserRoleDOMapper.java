package hk.ljx.fishhub.user.biz.domain.mapper;

import hk.ljx.fishhub.user.biz.domain.dataobject.UserRoleDO;

import java.util.List;

public interface UserRoleDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserRoleDO record);

    int insertSelective(UserRoleDO record);

    UserRoleDO selectByPrimaryKey(Long id);

    int insertDefaultRoleForUsersWithoutRole(Long roleId);

    List<UserRoleDO> selectEnabledList();

    int updateByPrimaryKeySelective(UserRoleDO record);

    int updateByPrimaryKey(UserRoleDO record);
}
