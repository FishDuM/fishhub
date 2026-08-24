package hk.ljx.fishhub.user.relation.biz.domain.mapper;

import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FollowingDOMapper {
    int deleteByPrimaryKey(Long id);

    int deleteByUserIdAndFollowingUserId(@Param("userId") Long userId,
                                         @Param("unfollowUserId") Long unfollowUserId);

    int insert(FollowingDO record);

    int insertIgnore(FollowingDO record);

    int insertSelective(FollowingDO record);

    FollowingDO selectByPrimaryKey(Long id);

    List<FollowingDO> selectByUserId(Long userId);

    List<FollowingDO> selectCursorPageByUserId(@Param("userId") Long userId,
                                               @Param("cursor") Long cursor,
                                               @Param("limit") long limit);

    /** 粉丝列表反向查询：following_user_id = 目标用户的记录，粉丝即该行 user_id */

    List<FollowingDO> selectCursorPageByFollowingUserId(@Param("followingUserId") Long followingUserId,
                                                        @Param("cursor") Long cursor,
                                                        @Param("limit") long limit);

    List<Long> selectFollowingUserIds(@Param("userId") Long userId,
                                      @Param("followingUserIds") List<Long> followingUserIds);


    int updateByPrimaryKeySelective(FollowingDO record);

    int updateByPrimaryKey(FollowingDO record);
}
