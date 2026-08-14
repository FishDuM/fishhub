package hk.ljx.fishhub.user.biz.domain.mapper;

import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserDO record);

    int insertSelective(UserDO record);

    UserDO selectByPrimaryKey(Long id);

    /**
     * 根据手机号查询记录
     * @param phone
     * @return
     */
    UserDO selectByPhone(String phone);

    /**
     * 查询可用于认证的账户。注册查重仍使用 selectByPhone，不能把已注销手机号误判为可重新注册。
     *
     * @param phone 手机号
     * @return 已启用且未删除的用户
     */
    UserDO selectActiveByPhone(String phone);

    /**
     * 批量查询用户信息
     *
     * @param ids
     * @return
     */
    List<UserDO> selectByIds(@Param("ids") List<Long> ids);

    int updateByPrimaryKeySelective(UserDO record);

    int updateByPrimaryKey(UserDO record);
}
