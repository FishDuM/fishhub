package hk.ljx.fishhub.data.align.domain.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 更新
 */
public interface UpdateMapper {

    /**
     * 更新 t_user_count 计数表总关注数
     * @param userId
     * @return
     */
    int updateUserFollowingTotalByUserId(@Param("userId") long userId,
                                         @Param("followingTotal") long followingTotal);

    /**
     * 更新 t_user_count 计数表总粉丝数
     */
    int updateUserFansTotalByUserId(@Param("userId") long userId,
                                         @Param("fansTotal") long fansTotal);

    /**
     * 更新 t_user_count 计数表获得的总点赞数
     */
    int updateUserLikeTotalByUserId(@Param("userId") long userId,
                                    @Param("likeTotal") long likeTotal);

    /**
     * 更新 t_user_count 计数表获得的总收藏数
     */
    int updateUserCollectTotalByUserId(@Param("userId") long userId,
                                    @Param("collectTotal") long collectTotal);

    /**
     * 更新 t_user_count 计数表获得的总笔记发布数
     */
    int updateUserNoteTotalByUserId(@Param("userId") long userId,
                                       @Param("noteTotal") long noteTotal);

    /**
     * 更新 t_note_count 计数表笔记点赞数
     */
    int updateNoteLikeTotalByUserId(@Param("noteId") long noteId,
                                         @Param("noteLikeTotal") long noteLikeTotal);

    /**
     * 更新 t_note_count 计数表笔记收藏数
     */
    int updateNoteCollectTotalByUserId(@Param("noteId") long noteId,
                                    @Param("noteCollectTotal") long noteCollectTotal);
}
