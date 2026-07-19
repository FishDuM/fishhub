package hk.ljx.fishhub.user.biz.domain.mapper;

import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;

public interface UserDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserDO record);

    int insertSelective(UserDO record);

    UserDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(UserDO record);

    int updateByPrimaryKey(UserDO record);

    /**
     * 根据手机号查询用户
     * @param phone
     * @return
     */
    UserDO selectByPhone(String phone);
}