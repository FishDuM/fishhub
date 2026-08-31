package hk.ljx.fishhub.comment.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.constant.DateConstants;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.*;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.PublishCommentMqDTO;
import hk.ljx.fishhub.comment.biz.model.vo.*;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentDO;
import hk.ljx.fishhub.comment.biz.domain.repository.CommentContentRepository;
import hk.ljx.fishhub.comment.biz.rpc.DistributedIdGeneratorRpcService;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.service.CommentCacheService;
import hk.ljx.fishhub.comment.biz.service.CommentChangedLocalHandler;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.comment.biz.service.CommentLikeRealtimeService;
import hk.ljx.fishhub.comment.biz.service.CommentService;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import hk.ljx.framework.mq.support.RocketMqHelper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final NoteRpcService noteRpcService;
    private final DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    private final CommentContentRepository commentContentRepository;
    private final UserClient userClient;
    private final CommentDOMapper commentDOMapper;
    private final CommentCacheService commentCacheService;
    private final CommentDetailCache commentDetailCache;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final RocketMQTemplate rocketMQTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CommentLikeRealtimeService commentLikeRealtimeService;

    /** 一级热点评论分页本地短缓存（3 秒）：极大降低高并发下对同一笔记热度评论的重复锁竞争与 Feign/DB 穿透 */
    private static final Cache<String, PageResponse<FindCommentItemRspVO>> COMMENT_PAGE_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(500)
            .maximumSize(5000)
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .build();

    /** 二级子评论分页本地短缓存（3 秒） */
    private static final Cache<String, PageResponse<FindChildCommentItemRspVO>> CHILD_COMMENT_PAGE_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(500)
            .maximumSize(5000)
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .build();




    /**
     * 发布评论
     *
     * @param publishCommentReqVO
     * @return
     */
    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        String content = publishCommentReqVO.getContent();
        String imageUrl = publishCommentReqVO.getImageUrl();

        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),
                "评论正文和图片不能同时为空");

        Long noteId = publishCommentReqVO.getNoteId();
        Long replyCommentId = publishCommentReqVO.getReplyCommentId();

        Long creatorId = LoginUserContextHolder.getUserId();

        // 同步前置校验笔记可写性（防止静默丢弃与假成功）
        if (!noteRpcService.isWritable(noteId, creatorId)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_WRITABLE);
        }

        String commentId = distributedIdGeneratorRpcService.generateCommentId();

        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .commentId(Long.valueOf(commentId))
                .noteId(noteId)
                .content(content)
                .imageUrl(imageUrl)
                .replyCommentId(replyCommentId)
                .createTime(LocalDateTime.now())
                .creatorId(creatorId)
                .build();

        String publishMsg = JsonUtils.toJsonString(publishCommentMqDTO);
        // 同步发送：失败即抛，避免"发布成功"但评论永不落库
        RocketMqHelper.syncSend(rocketMQTemplate, MQConstants.TOPIC_PUBLISH_COMMENT, publishMsg, "发布评论");

        return Response.success(Long.valueOf(commentId));
    }

    /**
     * 评论列表分页查询
     *
     * @param findCommentPageListReqVO
     * @return
     */
    @Override
    public PageResponse<FindCommentItemRspVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO) {
        Long noteId = findCommentPageListReqVO.getNoteId();
        Integer pageNo = findCommentPageListReqVO.getPageNo();
        long pageSize = 10;

        String localCacheKey = noteId + ":" + pageNo;
        PageResponse<FindCommentItemRspVO> localCached = COMMENT_PAGE_LOCAL_CACHE.getIfPresent(localCacheKey);
        if (localCached != null) {
            return localCached;
        }

        ensureNoteAccessible(noteId);

        PageResponse<FindCommentItemRspVO> response = doFindCommentPageList(noteId, pageNo, pageSize);
        if (response != null && CollUtil.isNotEmpty(response.getData())) {
            COMMENT_PAGE_LOCAL_CACHE.put(localCacheKey, response);
        }
        return response;
    }

    private PageResponse<FindCommentItemRspVO> doFindCommentPageList(Long noteId, Integer pageNo, long pageSize) {
        // 一级评论数单独缓存；不能拿包含回复数的笔记评论总数替代。
        long count = commentCacheService.getOneLevelCommentTotal(noteId, () -> queryOneLevelCommentTotal(noteId));

        if (count == 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, 0, pageSize);
        }

        List<FindCommentItemRspVO> commentRspVOS = Lists.newArrayList();
        long offset = PageResponse.getOffset(pageNo, pageSize);

        boolean hasKey = commentCacheService.hasCommentListZSet(noteId);

        // 若不存在且查询前 50 页热点数据，单飞抢锁重建或等待已抢锁线程建好，防止并发击穿数据库
        if (!hasKey && offset < 500) {
            if (commentCacheService.tryLockCommentListRebuild(noteId)) {
                try {
                    if (!commentCacheService.hasCommentListZSet(noteId)) {
                        List<CommentDO> heatComments = commentDOMapper.selectHeatComments(noteId);
                        commentCacheService.syncHeatComments(noteId, heatComments);
                    }
                } finally {
                    commentCacheService.unlockCommentListRebuild(noteId);
                }
            }
            hasKey = commentCacheService.hasCommentListZSet(noteId);
        }

        if (hasKey && offset < 500) {
            Set<String> commentIds = commentCacheService.getCommentIdsByZSet(noteId, offset, pageSize);

            if (CollUtil.isNotEmpty(commentIds)) {
                List<Long> commentIdList = commentIds.stream()
                        .map(Long::valueOf)
                        .toList();

                List<String> commentIdKeys = commentIdList.stream()
                        .map(RedisKeyConstants::buildCommentDetailKey)
                        .toList();

                List<String> commentsJsonList = commentCacheService.multiGetCommentDetails(commentIdKeys);

                Map<Long, FindCommentItemRspVO> detailMap = new HashMap<>();
                List<Long> expiredCommentIds = Lists.newArrayList();

                for (int i = 0; i < commentsJsonList.size(); i++) {
                    String commentJson = commentsJsonList.get(i);
                    Long commentId = commentIdList.get(i);
                    if (Objects.nonNull(commentJson)) {
                        FindCommentItemRspVO commentRspVO = JsonUtils.parseObject(commentJson, FindCommentItemRspVO.class);
                        if (commentRspVO != null) {
                            detailMap.put(commentId, commentRspVO);
                        }
                    } else {
                        expiredCommentIds.add(commentId);
                    }
                }

                if (CollUtil.isNotEmpty(expiredCommentIds)) {
                    List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(expiredCommentIds);
                    List<FindCommentItemRspVO> dbFetchedVOs = Lists.newArrayList();
                    getCommentDataAndSync2Redis(commentDOS, noteId, dbFetchedVOs);
                    for (FindCommentItemRspVO vo : dbFetchedVOs) {
                        if (vo != null && vo.getCommentId() != null) {
                            detailMap.put(vo.getCommentId(), vo);
                        }
                    }
                }

                if (CollUtil.isNotEmpty(detailMap)) {
                    setCommentCountData(new ArrayList<>(detailMap.values()), expiredCommentIds);
                }

                for (Long commentId : commentIdList) {
                    FindCommentItemRspVO vo = detailMap.get(commentId);
                    if (vo != null) {
                        commentRspVOS.add(vo);
                    }
                }
            }

            return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
        }

        List<CommentDO> oneLevelCommentDOS = commentDOMapper.selectPageList(noteId, offset, pageSize);
        getCommentDataAndSync2Redis(oneLevelCommentDOS, noteId, commentRspVOS);
        if (CollUtil.isNotEmpty(commentRspVOS)) {
            setCommentCountData(commentRspVOS, Collections.emptyList());
        }

        return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
    }

    /**
     * 二级评论分页查询
     *
     * @param findChildCommentPageListReqVO
     * @return
     */
    @Override
    public PageResponse<FindChildCommentItemRspVO> findChildCommentPageList(FindChildCommentPageListReqVO findChildCommentPageListReqVO) {
        Long parentCommentId = findChildCommentPageListReqVO.getParentCommentId();
        Integer pageNo = findChildCommentPageListReqVO.getPageNo();
        long pageSize = 6;

        String localCacheKey = parentCommentId + ":" + pageNo;
        PageResponse<FindChildCommentItemRspVO> localCached = CHILD_COMMENT_PAGE_LOCAL_CACHE.getIfPresent(localCacheKey);
        if (localCached != null) {
            return localCached;
        }

        CommentDO parentComment = commentDOMapper.selectByPrimaryKey(parentCommentId);
        if (parentComment == null) {
            throw new BizException(ResponseCodeEnum.PARENT_COMMENT_NOT_FOUND);
        }
        ensureNoteAccessible(parentComment.getNoteId());

        PageResponse<FindChildCommentItemRspVO> response = doFindChildCommentPageList(parentCommentId, pageNo, pageSize);
        if (response != null && CollUtil.isNotEmpty(response.getData())) {
            CHILD_COMMENT_PAGE_LOCAL_CACHE.put(localCacheKey, response);
        }
        return response;
    }

    private PageResponse<FindChildCommentItemRspVO> doFindChildCommentPageList(Long parentCommentId, Integer pageNo, long pageSize) {
        Long redisCount = commentCacheService.getChildCommentTotal(parentCommentId);
        long count = Objects.isNull(redisCount) ? 0L : redisCount;

        if (Objects.isNull(redisCount)) {
            // 查询一级评论下子评论的总数 (直接查询 t_comment 表的 child_comment_total 字段，提升查询性能, 避免 count(*))
            List<CommentDO> countRecords = commentDOMapper.selectCommentCountByIds(List.of(parentCommentId));
            CommentDO countRecord = CollUtil.isEmpty(countRecords) ? null : countRecords.get(0);

            if (Objects.isNull(countRecord)) {
                throw new BizException(ResponseCodeEnum.PARENT_COMMENT_NOT_FOUND);
            }

            Long childTotal = countRecord.getChildCommentTotal();
            count = Objects.isNull(childTotal) ? 0L : childTotal;
            threadPoolTaskExecutor.execute(() -> commentCacheService.putCommentCount(parentCommentId, countRecord.getChildCommentTotal(), countRecord.getLikeTotal()));
        }

        if (count == 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, 0, pageSize);
        }

        List<FindChildCommentItemRspVO> childCommentRspVOS = Lists.newArrayList();

        // 计算分页查询的偏移量 offset (需要 +1，因为最早回复的二级评论已经被展示了)
        long offset = PageResponse.getOffset(pageNo, pageSize) + 1;

        boolean hasKey = commentCacheService.hasChildCommentListZSet(parentCommentId);

        // 若不存在且查询的是热点页（前 10 页且落在缓存窗口内），单飞抢锁重建或等待已抢锁线程建好，防止并发击穿数据库
        if (!hasKey && offset < 6 * 10
                && offset >= Math.max(0L, count - CommentChangedLocalHandler.CHILD_COMMENT_LIST_MAX_SIZE)) {
            if (commentCacheService.tryLockChildCommentListRebuild(parentCommentId)) {
                try {
                    if (!commentCacheService.hasChildCommentListZSet(parentCommentId)) {
                        List<CommentDO> childCommentDOS = commentDOMapper.selectLatestChildCommentsByParentIdAndLimit(
                                parentCommentId, (int) CommentChangedLocalHandler.CHILD_COMMENT_LIST_MAX_SIZE);
                        commentCacheService.syncChildComments(parentCommentId, childCommentDOS);
                    }
                } finally {
                    commentCacheService.unlockChildCommentListRebuild(parentCommentId);
                }
            }
            hasKey = commentCacheService.hasChildCommentListZSet(parentCommentId);
        }

        if (hasKey && offset < count) {
            Long cachedSize = commentCacheService.getChildCommentZSetCard(parentCommentId);
            long cacheStart = Math.max(0L, count - (cachedSize == null ? 0L : cachedSize));
            if (offset >= cacheStart) {
                long rank = offset - cacheStart;
                Set<String> childCommentIds = commentCacheService.getChildCommentIdsByZSet(parentCommentId, rank, pageSize);

                if (CollUtil.isNotEmpty(childCommentIds)) {
                    List<String> childCommentIdList = Lists.newArrayList(childCommentIds);

                    List<String> commentIdKeys = childCommentIds.stream()
                            .map(RedisKeyConstants::buildCommentDetailKey)
                            .toList();

                    List<String> commentsJsonList = commentCacheService.multiGetCommentDetails(commentIdKeys);

                    List<Long> expiredChildCommentIds = Lists.newArrayList();

                    for (int i = 0; i < commentsJsonList.size(); i++) {
                        String commentJson = commentsJsonList.get(i);
                        Long commentId = Long.valueOf(childCommentIdList.get(i));
                        if (Objects.nonNull(commentJson)) {
                            FindChildCommentItemRspVO childCommentRspVO = JsonUtils.parseObject(commentJson, FindChildCommentItemRspVO.class);
                            childCommentRspVOS.add(childCommentRspVO);
                        } else {
                            expiredChildCommentIds.add(commentId);
                        }
                    }

                    if (CollUtil.isNotEmpty(expiredChildCommentIds)) {
                        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(expiredChildCommentIds);
                        getChildCommentDataAndSync2Redis(commentDOS, childCommentRspVOS);
                    }

                    if (CollUtil.isNotEmpty(childCommentRspVOS)) {
                        setChildCommentCountData(childCommentRspVOS, expiredChildCommentIds);
                    }

                    childCommentRspVOS = childCommentRspVOS.stream()
                            .sorted(Comparator.comparing(FindChildCommentItemRspVO::getCommentId))
                            .collect(Collectors.toList());

                    return PageResponse.success(childCommentRspVOS, pageNo, count, pageSize);
                }
            }
        }

        List<CommentDO> childCommentDOS = commentDOMapper.selectChildPageList(parentCommentId, offset, pageSize);

        getChildCommentDataAndSync2Redis(childCommentDOS, childCommentRspVOS);
        if (CollUtil.isNotEmpty(childCommentRspVOS)) {
            setChildCommentCountData(childCommentRspVOS, Collections.emptyList());
        }

        return PageResponse.success(childCommentRspVOS, pageNo, count, pageSize);
    }

    /**
     * 评论点赞
     *
     * @param likeCommentReqVO
     * @return
     */
    @Override
    public Response<?> likeComment(LikeCommentReqVO likeCommentReqVO) {
        Long commentId = likeCommentReqVO.getCommentId();
        Long userId = LoginUserContextHolder.getUserId();

        CommentDO commentDO = commentDOMapper.selectByPrimaryKey(commentId);
        if (commentDO == null) {
            throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
        }

        // 同步前置校验笔记可写性（防止静默丢弃与数据撕裂）
        if (!noteRpcService.isWritable(commentDO.getNoteId(), userId)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_WRITABLE);
        }

        if (commentLikeRealtimeService.containsLiked(userId, commentId)) {
            throw new BizException(ResponseCodeEnum.COMMENT_ALREADY_LIKED);
        }

        LikeUnlikeCommentMqDTO likeUnlikeCommentMqDTO = LikeUnlikeCommentMqDTO.builder()
                .userId(userId)
                .commentId(commentId)
                .type(LikeUnlikeCommentTypeEnum.LIKE.getCode())
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeCommentMqDTO))
                .build();

        String destination = MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;

        // 分区键：同一评论操作路由到同一队列，保证消费端串行物理消除死锁
        String hashKey = String.valueOf(commentId);

        // 先更新实时状态，再发送 MQ；发送彻底失败时由 rollback 清缓存回源。
        commentLikeRealtimeService.markLiked(userId, commentId);
        if (Objects.equals(commentDO.getLevel(), CommentLevelEnum.ONE.getCode())) {
            commentLikeRealtimeService.incrementCommentHeat(commentDO.getNoteId(), commentId, 1.0);
        }

        try {
            RocketMqHelper.syncSendOrderly(rocketMQTemplate, destination, message, hashKey, "评论点赞");
        } catch (Exception e) {
            // 发送失败：清空实时点赞缓存后向上抛出，避免假成功
            commentLikeRealtimeService.evictLikeState(userId, commentId);
            throw e;
        }

        return Response.success();
    }

    /**
     * 取消评论点赞
     *
     * @param unlikeCommentReqVO
     * @return
     */
    @Override
    public Response<?> unlikeComment(UnlikeCommentReqVO unlikeCommentReqVO) {
        Long commentId = unlikeCommentReqVO.getCommentId();
        Long userId = LoginUserContextHolder.getUserId();

        // 取消点赞仅校验当前用户点赞状态（纯 Redis 守卫，0 DB 读；即使评论/笔记已被删除，也必须允许用户取消点赞并清理足迹）
        // 注：未再次主动取消的点赞足迹随 7 天 Redis ZSet TTL 自然过期自愈收敛
        if (!commentLikeRealtimeService.containsLiked(userId, commentId)) {
            throw new BizException(ResponseCodeEnum.COMMENT_NOT_LIKED);
        }

        LikeUnlikeCommentMqDTO likeUnlikeCommentMqDTO = LikeUnlikeCommentMqDTO.builder()
                .userId(userId)
                .commentId(commentId)
                .type(LikeUnlikeCommentTypeEnum.UNLIKE.getCode())
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeCommentMqDTO))
                .build();

        String destination = MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;

        // 分区键：同一评论操作路由到同一队列，保证消费端串行物理消除死锁
        String hashKey = String.valueOf(commentId);

        // 先更新实时状态，再发送 MQ；发送彻底失败时由 rollback 清缓存回源。
        commentLikeRealtimeService.markUnliked(userId, commentId);

        try {
            RocketMqHelper.syncSendOrderly(rocketMQTemplate, destination, message, hashKey, "评论取消点赞");
        } catch (Exception e) {
            // 发送失败：清空实时点赞缓存后向上抛出，避免假成功
            commentLikeRealtimeService.evictLikeState(userId, commentId);
            throw e;
        }

        return Response.success();
    }

    @Override
    public Response<List<Long>> findLikedCommentIds(FindLikedCommentIdsReqVO reqVO) {
        Long userId = LoginUserContextHolder.getUserId();
        List<Long> commentIds = reqVO.getCommentIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(commentIds)) {
            return Response.success(Collections.emptyList());
        }
        ensureCommentsAccessible(commentIds);
        return Response.success(commentLikeRealtimeService.filterLikedCommentIds(userId, commentIds));
    }

    /**
     * 查询点赞足迹分页
     */
    @Override
    public PageResponse<FindLikedCommentItemRspVO> findLikedCommentPage(FindLikedCommentPageReqVO reqVO) {
        Long userId = LoginUserContextHolder.getUserId();
        int pageNo = reqVO.getPageNo();
        int pageSize = reqVO.getPageSize() == null ? 10 : Math.min(Math.max(reqVO.getPageSize(), 1), 50);

        CommentLikeRealtimeService.LikedCommentPage page =
                commentLikeRealtimeService.pageLikedCommentIds(userId, pageNo, pageSize);
        if (CollUtil.isEmpty(page.commentIds())) {
            return PageResponse.success(Collections.emptyList(), pageNo, page.total());
        }

        List<CommentDO> comments = commentDOMapper.selectByCommentIds(page.commentIds());
        if (CollUtil.isEmpty(comments)) {
            return PageResponse.success(Collections.emptyList(), pageNo, page.total());
        }

        List<Long> noteIds = comments.stream().map(CommentDO::getNoteId).distinct().toList();
        Set<Long> accessibleNoteIds = new HashSet<>(noteRpcService.findAccessibleNoteIds(noteIds));
        List<CommentDO> accessibleComments = comments.stream()
                .filter(comment -> accessibleNoteIds.contains(comment.getNoteId()))
                .toList();
        if (CollUtil.isEmpty(accessibleComments)) {
            return PageResponse.success(Collections.emptyList(), pageNo, page.total());
        }

        Map<String, String> contentByUuid = batchFindCommentContents(accessibleComments);

        List<Long> authorIds = accessibleComments.stream()
                .map(CommentDO::getUserId)
                .distinct()
                .toList();
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = Collections.emptyMap();
        if (CollUtil.isNotEmpty(authorIds)) {
            List<FindUserByIdRspDTO> users = userClient.findByIds(authorIds);
            if (CollUtil.isNotEmpty(users)) {
                userIdAndDTOMap = users.stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, Function.identity(), (a, b) -> a));
            }
        }

        Map<Long, FindUserByIdRspDTO> finalUserMap = userIdAndDTOMap;
        List<FindLikedCommentItemRspVO> items = accessibleComments.stream().map(comment -> {
            FindUserByIdRspDTO author = finalUserMap.get(comment.getUserId());
            String content = null;
            if (!Boolean.TRUE.equals(comment.getIsContentEmpty()) && StringUtils.isNotBlank(comment.getContentUuid())) {
                content = contentByUuid.get(comment.getContentUuid());
            }
            return FindLikedCommentItemRspVO.builder()
                    .commentId(comment.getId())
                    .noteId(comment.getNoteId())
                    .userId(comment.getUserId())
                    .avatar(author != null ? author.getAvatar() : null)
                    .nickname(author != null ? author.getNickName() : null)
                    .content(content)
                    .imageUrl(comment.getImageUrl())
                    .likeTime(DateUtils.localDateTime2String(comment.getCreateTime()))
                    .likeTotal(comment.getLikeTotal())
                    .build();
        }).toList();

        return PageResponse.success(items, pageNo, page.total());
    }

    /**
     * 按笔记分组批量从 Cassandra 取评论正文，返回 contentUuid -> content
     */
    private Map<String, String> batchFindCommentContents(List<CommentDO> comments) {
        Map<String, String> contentByUuid = Maps.newHashMap();
        Map<Long, List<CommentDO>> byNote = comments.stream()
                .collect(Collectors.groupingBy(CommentDO::getNoteId));
        byNote.forEach((noteId, noteComments) -> {
            contentByUuid.putAll(findCommentContentMap(noteId, noteComments));
        });
        return contentByUuid;
    }

    private Map<String, String> findCommentContentMap(Long noteId, List<CommentDO> comments) {
        if (CollUtil.isEmpty(comments)) {
            return Collections.emptyMap();
        }
        Map<String, List<UUID>> contentIdsByYearMonth = comments.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsContentEmpty())
                        && StringUtils.isNotBlank(c.getContentUuid())
                        && c.getCreateTime() != null)
                .collect(Collectors.groupingBy(
                        c -> DateConstants.DATE_FORMAT_Y_M.format(c.getCreateTime()),
                        Collectors.mapping(c -> UUID.fromString(c.getContentUuid()), Collectors.toList())
                ));

        Map<String, String> resultMap = Maps.newHashMap();
        contentIdsByYearMonth.forEach((yearMonth, uuids) -> {
            if (CollUtil.isNotEmpty(uuids)) {
                try {
                    List<CommentContentDO> contentDOs = commentContentRepository
                            .findByNoteIdAndYearMonthAndContentIdIn(noteId, yearMonth, uuids);
                    if (CollUtil.isNotEmpty(contentDOs)) {
                        for (CommentContentDO doc : contentDOs) {
                            if (doc != null && doc.getPrimaryKey() != null && doc.getPrimaryKey().getContentId() != null) {
                                resultMap.put(doc.getPrimaryKey().getContentId().toString(), doc.getContent());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Cassandra 查询评论内容异常或超时，执行优雅降级, noteId={}, yearMonth={}, msg={}", noteId, yearMonth, e.getMessage());
                }
            }
        });
        return resultMap;
    }

    /**
     * 删除评论
     *
     * @param deleteCommentReqVO
     * @return
     */
    @Override
    public Response<?> deleteComment(DeleteCommentReqVO deleteCommentReqVO) {
        Long commentId = deleteCommentReqVO.getCommentId();

        CommentDO commentDO = commentDOMapper.selectByPrimaryKey(commentId);

        if (Objects.isNull(commentDO)) {
            throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
        }
        ensureNoteAccessible(commentDO.getNoteId());

        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, commentDO.getUserId())) {
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        }

        // 整个删除树由消费端完整处理；Broker 未确认前不删除主评论。
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(commentDO))
                .build();

        // 同步发送：失败即抛，避免"删除成功"但评论永不删除
        RocketMqHelper.syncSend(rocketMQTemplate, MQConstants.TOPIC_DELETE_COMMENT, message, "删除评论");

        return Response.success();
    }

    private void ensureNoteAccessible(Long noteId) {
        if (!noteRpcService.isAccessible(noteId)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
    }

    private void ensureCommentsAccessible(List<Long> commentIds) {
        List<CommentDO> comments = commentDOMapper.selectNoteIdsByCommentIds(commentIds);
        if (comments.size() != commentIds.size()) {
            throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
        }
        List<Long> noteIds = comments.stream().map(CommentDO::getNoteId).distinct().toList();
        Set<Long> accessibleNoteIds = new HashSet<>(noteRpcService.findAccessibleNoteIds(noteIds));
        if (accessibleNoteIds.size() != noteIds.size()) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
    }

    /**
     * 设置子评论 VO 的计数
     *
     * @param commentRspVOS 返参 VO 集合
     * @param expiredCommentIds 缓存中已失效的评论 ID 集合
     */
    private void setChildCommentCountData(List<FindChildCommentItemRspVO> commentRspVOS,
                                          List<Long> expiredCommentIds) {
        List<Long> allChildCommentIds = Lists.newArrayList();

        commentRspVOS.forEach(commentRspVO -> {
            Long childCommentId = commentRspVO.getCommentId();
            allChildCommentIds.add(childCommentId);
        });

        Map<Long, Map<String, String>> commentIdAndCountMap = getCommentCountDataAndSync2RedisHash(allChildCommentIds);

        for (FindChildCommentItemRspVO commentRspVO : commentRspVOS) {
            Long commentId = commentRspVO.getCommentId();

            // 设置子评论的点赞数（Hash 有则覆盖，无则保持 DB 初始值）
            Map<String, String> hash = commentIdAndCountMap.get(commentId);
            if (CollUtil.isNotEmpty(hash)) {
                String likeTotalObj = hash.get(CountKeyConstants.FIELD_LIKE_TOTAL);
                Long likeTotal = Objects.isNull(likeTotalObj) ? 0 : Long.parseLong(likeTotalObj);
                commentRspVO.setLikeTotal(likeTotal);
            }
        }
    }

    /**
     * 获取评论计数数据，并同步到 Redis 中
     * @param notExpiredCommentIds
     * @return
     */
    private Map<Long, Map<String, String>> getCommentCountDataAndSync2RedisHash(List<Long> notExpiredCommentIds) {
        if (CollUtil.isEmpty(notExpiredCommentIds)) {
            return Collections.emptyMap();
        }

        Map<Long, Map<String, String>> commentIdAndCountMap = new HashMap<>(commentCacheService.batchGetCommentCounts(notExpiredCommentIds));
        List<Long> expiredCountCommentIds = notExpiredCommentIds.stream()
                .filter(id -> !commentIdAndCountMap.containsKey(id))
                .toList();

        if (CollUtil.isNotEmpty(expiredCountCommentIds)) {
            List<CommentDO> commentDOS = commentDOMapper.selectCommentCountByIds(expiredCountCommentIds);
            if (CollUtil.isNotEmpty(commentDOS)) {
                Map<Long, Map<String, String>> freshCounts = new HashMap<>(commentDOS.size());
                commentDOS.forEach(commentDO -> {
                    Integer level = commentDO.getLevel();
                    Map<String, String> map = Maps.newHashMap();
                    map.put(CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(commentDO.getLikeTotal()));
                    // 只有一级评论需要统计子评论总数
                    if (Objects.equals(level, CommentLevelEnum.ONE.getCode())) {
                        map.put(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL, String.valueOf(commentDO.getChildCommentTotal()));
                    }
                    freshCounts.put(commentDO.getId(), map);
                    commentIdAndCountMap.put(commentDO.getId(), map);
                });

                threadPoolTaskExecutor.execute(() -> commentCacheService.batchPutCommentCounts(freshCounts));
            }
        }
        return commentIdAndCountMap;
    }

    /**
     * 获取子评论列表，并同步到 Redis 中
     * @param childCommentDOS
     * @param childCommentRspVOS
     */
    private void getChildCommentDataAndSync2Redis(List<CommentDO> childCommentDOS, List<FindChildCommentItemRspVO> childCommentRspVOS) {
        Set<Long> userIds = Sets.newHashSet();
        Long noteId = null;

        for (CommentDO childCommentDO : childCommentDOS) {
            noteId = childCommentDO.getNoteId();
            userIds.add(childCommentDO.getUserId());

            Long parentId = childCommentDO.getParentId();
            Long replyCommentId = childCommentDO.getReplyCommentId();
            if (!Objects.equals(parentId, replyCommentId)) {
                userIds.add(childCommentDO.getReplyUserId());
            }
        }

        Map<String, String> commentUuidAndContentMap = findCommentContentMap(noteId, childCommentDOS);

        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userClient.findByIds(userIds.stream().toList());

        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = Collections.emptyMap();
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            userIdAndDTOMap = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto, (a, b) -> a));
        }


        for (CommentDO childCommentDO : childCommentDOS) {
            Long userId = childCommentDO.getUserId();
            FindChildCommentItemRspVO childCommentRspVO = FindChildCommentItemRspVO.builder()
                    .userId(userId)
                    .commentId(childCommentDO.getId())
                    .imageUrl(childCommentDO.getImageUrl())
                    .createTime(DateUtils.localDateTime2String(childCommentDO.getCreateTime()))
                    .likeTotal(childCommentDO.getLikeTotal())
                    .build();

            FindUserByIdRspDTO author = userIdAndDTOMap.get(userId);
            if (author != null) {
                childCommentRspVO.setAvatar(author.getAvatar());
                childCommentRspVO.setNickname(author.getNickName());
            }

            Long replyCommentId = childCommentDO.getReplyCommentId();
            Long parentId = childCommentDO.getParentId();

            if (replyCommentId != null && !Objects.equals(replyCommentId, parentId)) {
                FindUserByIdRspDTO replyUser = userIdAndDTOMap.get(childCommentDO.getReplyUserId());
                if (replyUser != null) {
                    childCommentRspVO.setReplyUserName(replyUser.getNickName());
                    childCommentRspVO.setReplyUserId(replyUser.getId());
                }
            }

            if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
                String contentUuid = childCommentDO.getContentUuid();
                if (StringUtils.isNotBlank(contentUuid)) {
                    childCommentRspVO.setContent(commentUuidAndContentMap.get(contentUuid));
                }
            }

            childCommentRspVOS.add(childCommentRspVO);
        }

        threadPoolTaskExecutor.execute(() -> {
            Map<String, String> data = Maps.newHashMap();
            childCommentRspVOS.forEach(commentRspVO -> {
                Long commentId = commentRspVO.getCommentId();
                String key = RedisKeyConstants.buildCommentDetailKey(commentId);
                data.put(key, JsonUtils.toJsonString(commentRspVO));
            });

            commentDetailCache.putAll(data);
        });
    }

    private long queryOneLevelCommentTotal(Long noteId) {
        return Objects.requireNonNullElse(commentDOMapper.selectOneLevelCountByNoteId(noteId), 0L);
    }


    /**
     * 设置评论 VO 的计数
     *
     * @param commentRspVOS 返参 VO 集合
     * @param expiredCommentIds 缓存中已失效的评论 ID 集合
     */
    private void setCommentCountData(List<FindCommentItemRspVO> commentRspVOS,
                                     List<Long> expiredCommentIds) {
        List<Long> allCommentIds = Lists.newArrayList();

        commentRspVOS.forEach(commentRspVO -> {
            Long oneLevelCommentId = commentRspVO.getCommentId();
            allCommentIds.add(oneLevelCommentId);
            FindCommentItemRspVO firstCommentVO = commentRspVO.getFirstReplyComment();
            if (Objects.nonNull(firstCommentVO)) {
                allCommentIds.add(firstCommentVO.getCommentId());
            }
        });

        Map<Long, Map<String, String>> commentIdAndCountMap = getCommentCountDataAndSync2RedisHash(allCommentIds);

        for (FindCommentItemRspVO commentRspVO : commentRspVOS) {
            Long commentId = commentRspVO.getCommentId();

            Map<String, String> hash = commentIdAndCountMap.get(commentId);
            if (CollUtil.isNotEmpty(hash)) {
                String likeTotalObj = hash.get(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL);
                Long childCommentTotal = Objects.isNull(likeTotalObj) ? 0 : Long.parseLong(likeTotalObj);
                String likeTotalFieldObj = hash.get(CountKeyConstants.FIELD_LIKE_TOTAL);
                Long likeTotal = Objects.isNull(likeTotalFieldObj) ? 0 : Long.parseLong(likeTotalFieldObj);
                commentRspVO.setChildCommentTotal(childCommentTotal);
                commentRspVO.setLikeTotal(likeTotal);
                FindCommentItemRspVO firstCommentVO = commentRspVO.getFirstReplyComment();
                if (Objects.nonNull(firstCommentVO)) {
                    Long firstCommentId = firstCommentVO.getCommentId();
                    Map<String, String> firstCommentHash = commentIdAndCountMap.get(firstCommentId);
                    if (CollUtil.isNotEmpty(firstCommentHash)) {
                        String firstLikeTotalObj = firstCommentHash.get(CountKeyConstants.FIELD_LIKE_TOTAL);
                        Long firstCommentLikeTotal = Objects.isNull(firstLikeTotalObj) ? 0 : Long.parseLong(firstLikeTotalObj);
                        firstCommentVO.setLikeTotal(firstCommentLikeTotal);
                    }
                }
            }
        }
    }


    /**
     * 获取全部评论数据，并将评论详情同步到 Redis 中
     * @param oneLevelCommentDOS
     * @param noteId
     * @param commentRspVOS
     */
    private void getCommentDataAndSync2Redis(List<CommentDO> oneLevelCommentDOS, Long noteId, List<FindCommentItemRspVO> commentRspVOS) {
        List<Long> twoLevelCommentIds = oneLevelCommentDOS.stream()
                .map(CommentDO::getFirstReplyCommentId)
                .filter(firstReplyCommentId -> firstReplyCommentId != null && firstReplyCommentId != 0)
                .toList();

        Map<Long, CommentDO> commentIdAndDOMap = null;
        List<CommentDO> twoLevelCommonDOS = null;
        if (CollUtil.isNotEmpty(twoLevelCommentIds)) {
            twoLevelCommonDOS = commentDOMapper.selectTwoLevelCommentByIds(twoLevelCommentIds);

            commentIdAndDOMap = twoLevelCommonDOS.stream()
                    .collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO, (a, b) -> a));
        }

        List<Long> userIds = Lists.newArrayList();

        List<CommentDO> allCommentDOS = Lists.newArrayList();
        CollUtil.addAll(allCommentDOS, oneLevelCommentDOS);
        CollUtil.addAll(allCommentDOS, twoLevelCommonDOS);

        allCommentDOS.forEach(commentDO -> {
            userIds.add(commentDO.getUserId());
        });

        Map<String, String> commentUuidAndContentMap = findCommentContentMap(noteId, allCommentDOS);

        List<Long> distinctUserIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userClient.findByIds(distinctUserIds);

        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = Collections.emptyMap();
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            userIdAndDTOMap = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto, (a, b) -> a));
        }


        for (CommentDO commentDO : oneLevelCommentDOS) {
            FindCommentItemRspVO oneLevelCommentRspVO = toCommentItemVO(commentDO, userIdAndDTOMap, commentUuidAndContentMap);

            Long firstReplyCommentId = commentDO.getFirstReplyCommentId();
            if (CollUtil.isNotEmpty(commentIdAndDOMap) && firstReplyCommentId != null) {
                CommentDO firstReplyCommentDO = commentIdAndDOMap.get(firstReplyCommentId);
                if (firstReplyCommentDO != null) {
                    FindCommentItemRspVO firstReplyCommentRspVO = toCommentItemVO(firstReplyCommentDO, userIdAndDTOMap, commentUuidAndContentMap);
                    oneLevelCommentRspVO.setFirstReplyComment(firstReplyCommentRspVO);
                }
            }
            commentRspVOS.add(oneLevelCommentRspVO);
        }

        threadPoolTaskExecutor.execute(() -> {
            Map<String, String> data = Maps.newHashMap();
            commentRspVOS.forEach(commentRspVO -> {
                Long commentId = commentRspVO.getCommentId();
                String key = RedisKeyConstants.buildCommentDetailKey(commentId);
                data.put(key, JsonUtils.toJsonString(commentRspVO));
            });

            commentDetailCache.putAll(data);
        });
    }



    private static FindCommentItemRspVO toCommentItemVO(CommentDO commentDO,
                                                        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap,
                                                        Map<String, String> commentUuidAndContentMap) {
        FindCommentItemRspVO vo = FindCommentItemRspVO.builder()
                .userId(commentDO.getUserId())
                .commentId(commentDO.getId())
                .imageUrl(commentDO.getImageUrl())
                .createTime(DateUtils.localDateTime2String(commentDO.getCreateTime()))
                .likeTotal(commentDO.getLikeTotal())
                .childCommentTotal(commentDO.getChildCommentTotal())
                .heat(commentDO.getHeat())
                .build();
        setUserInfo(userIdAndDTOMap, commentDO.getUserId(), vo);
        setCommentContent(commentUuidAndContentMap, commentDO, vo);
        return vo;
    }

    private static void setCommentContent(Map<String, String> commentUuidAndContentMap, CommentDO commentDO, FindCommentItemRspVO commentRspVO) {
        if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
            String contentUuid = commentDO.getContentUuid();
            if (StringUtils.isNotBlank(contentUuid)) {
                commentRspVO.setContent(commentUuidAndContentMap.get(contentUuid));
            }
        }
    }

    private static void setUserInfo(Map<Long, FindUserByIdRspDTO> userIdAndDTOMap, Long userId, FindCommentItemRspVO commentRspVO) {
        if (CollUtil.isNotEmpty(userIdAndDTOMap)) {
            FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(userId);
            if (Objects.nonNull(findUserByIdRspDTO)) {
                commentRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                commentRspVO.setNickname(findUserByIdRspDTO.getNickName());
            }
        }
    }
}
