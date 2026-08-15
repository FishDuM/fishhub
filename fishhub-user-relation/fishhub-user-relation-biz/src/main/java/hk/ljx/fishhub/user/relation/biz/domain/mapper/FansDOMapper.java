package hk.ljx.fishhub.user.relation.biz.domain.mapper;

import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FansDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FansDOMapper {
    int deleteByPrimaryKey(Long id);

    int deleteByUserIdAndFansUserId(@Param("userId") Long userId,
                                    @Param("fansUserId") Long fansUserId);

    int insert(FansDO record);

    int insertIgnore(FansDO record);

    int insertSelective(FansDO record);

    FansDO selectByPrimaryKey(Long id);

    List<FansDO> selectCursorPageByUserId(@Param("userId") Long userId,
                                          @Param("cursor") Long cursor,
                                          @Param("limit") long limit);

    int updateByPrimaryKeySelective(FansDO record);

    int updateByPrimaryKey(FansDO record);

}
