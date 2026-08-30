package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 评论缓存总管服务：统一收敛所有评论详情、点赞/子评论计数 Hash、分页 ZSet 及一级评论总数防击穿缓存
 */
public interface CommentCacheService {

    // --- 评论详情缓存 (comment:detail:v2:*) ---
    List<String> multiGetCommentDetails(List<String> keys);
    void batchPutCommentDetails(Map<String, String> data);
    void evictCommentDetails(Collection<String> keys);

    // --- 评论计数缓存 (count:comment:*) ---
    Map<Long, Map<String, String>> batchGetCommentCounts(List<Long> commentIds);
    void batchPutCommentCounts(Map<Long, Map<String, String>> countData);
    void putCommentCount(Long commentId, Long childTotal, Long likeTotal);
    Long getChildCommentTotal(Long parentCommentId);

    // --- 一级评论分页总数缓存防击穿 (cache:comment:one-level-total:*) ---
    long getOneLevelCommentTotal(Long noteId, Supplier<Long> dbLoader);
    void invalidateOneLevelCommentTotal(Long noteId);

    // --- 评论列表/热度列表 ZSet (comment:list:*) ---
    boolean hasCommentListZSet(Long noteId);
    Set<String> getCommentIdsByZSet(Long noteId, long offset, long limit);
    void syncHeatComments(Long noteId, List<CommentDO> heatComments);
    boolean tryLockCommentListRebuild(Long noteId);
    void unlockCommentListRebuild(Long noteId);

    // --- 子评论列表 ZSet (comment:childList:*) ---
    boolean hasChildCommentListZSet(Long parentCommentId);
    Long getChildCommentZSetCard(Long parentCommentId);
    Set<String> getChildCommentIdsByZSet(Long parentCommentId, long offset, long limit);
    void syncChildComments(Long parentCommentId, List<CommentDO> childComments);
    boolean tryLockChildCommentListRebuild(Long parentCommentId);
    void unlockChildCommentListRebuild(Long parentCommentId);
}
