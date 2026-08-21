package hk.ljx.fishhub.search.biz.domain.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 查询
 */
public interface SelectMapper {

    /**
     * 查询笔记文档所需的全字段数据
     * @param noteId
     * @return
     */
    List<Map<String, Object>> selectEsNoteIndexData(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /**
     * 查询用户文档所需的全字段数据
     * @param userId
     * @return
     */
    List<Map<String, Object>> selectEsUserIndexData(@Param("userId") Long userId);

    /**
     * 批量查询笔记文档所需的全字段数据（仅返回仍可检索的公开正常笔记）
     */
    List<Map<String, Object>> selectEsNoteIndexDataByIds(@Param("noteIds") List<Long> noteIds);

    /**
     * 批量查询用户文档所需的全字段数据（仅返回仍启用的用户）
     */
    List<Map<String, Object>> selectEsUserIndexDataByIds(@Param("userIds") List<Long> userIds);
}