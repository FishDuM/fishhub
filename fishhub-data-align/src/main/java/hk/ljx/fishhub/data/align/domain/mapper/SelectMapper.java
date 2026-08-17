package hk.ljx.fishhub.data.align.domain.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 查询
 */
public interface SelectMapper {


    /**
     * 日增量表：关注数计数变更 - 批量查询
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignFollowingCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                               @Param("batchSize") int batchSize);

    /**
     * 查询 t_following 关注表，获取关注总数
     * @param userId
     * @return
     */
    int selectCountFromFollowingTableByUserId(long userId);

    /**
     * 日增量表：粉丝数计数变更 - 批量查询
     */
    List<Long> selectBatchFromDataAlignFansCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                               @Param("batchSize") int batchSize);

    /** 粉丝总数：t_following 中 following_user_id = 目标用户的记录数 */
    int selectCountFromFollowingTableByFollowingUserId(long userId);

    /** 粉丝总数：t_following 中 following_user_id = 目标用户的记录数 */

    /**
     * 日增量表：用户获得的点赞数计数变更 - 批量查询
     */
    List<Long> selectBatchFromDataAlignUserLikeCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                              @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_like 笔记点赞表，获取用户获得的点赞总数
     */
    int selectUserLikeCountFromNoteLikeTableByUserId(long userId);

    /**
     * 日增量表：用户获得的收藏数计数变更 - 批量查询
     */
    List<Long> selectBatchFromDataAlignUserCollectCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                              @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_collection 笔记收藏表，获取用户获得的收藏总数
     */
    int selectUserCollectCountFromNoteCollectionTableByUserId(long userId);

    /**
     * 日增量表：笔记点赞数变更 - 批量查询
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignNoteLikeCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                               @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_like 笔记点赞表，获取点赞总数
     * @param noteId
     * @return
     */
    int selectCountFromNoteLikeTableByUserId(long noteId);

    /**
     * 日增量表：笔记发布数变更 - 批量查询
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignNotePublishCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                              @Param("batchSize") int batchSize);

    /**
     * 查询 t_note 笔记表，获取用户发布的笔记总数
     */
    int selectCountFromNoteTableByUserId(long userId);

    /**
     * 日增量表：笔记收藏数变更 - 批量查询
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignNoteCollectCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                              @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_collection 笔记收藏表，获取收藏总数
     * @param noteId
     * @return
     */
    int selectCountFromNoteCollectionTableByUserId(long noteId);

    /**
     * 判断日增量临时表是否存在
     * @param tableName 完整表名
     * @return 1 存在，0 不存在
     */
    int selectTempTableExists(@Param("tableName") String tableName);
}
