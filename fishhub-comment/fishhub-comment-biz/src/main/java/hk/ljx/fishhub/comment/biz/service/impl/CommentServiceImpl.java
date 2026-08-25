package hk.ljx.fishhub.comment.biz.service.impl;

import hk.ljx.framework.common.util.CacheRebuildSupport;
import hk.ljx.framework.common.util.CacheTtl;
import hk.ljx.framework.common.util.RebuildLock;

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
import hk.ljx.fishhub.comment.biz.rpc.DistributedIdGeneratorRpcService;
import hk.ljx.fishhub.comment.biz.rpc.KeyValueRpcService;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.service.CommentChangedLocalHandler;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.comment.biz.service.CommentLikeRealtimeService;
import hk.ljx.fishhub.comment.biz.service.CommentService;
import hk.ljx.fishhub.comment.biz.kv.dto.req.FindCommentContentReqDTO;
import hk.ljx.fishhub.comment.biz.kv.dto.rsp.FindCommentContentRspDTO;
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
    private final KeyValueRpcService keyValueRpcService;
    private final UserClient userClient;
    private final CommentDOMapper commentDOMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CommentDetailCache commentDetailCache;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final RocketMQTemplate rocketMQTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RedissonClient redissonClient;
    private final CommentLikeRealtimeService commentLikeRealtimeService;

    private static final int CACHE_REBUILD_RETRY_TIMES = 3;
    private static final long CACHE_REBUILD_RETRY_INTERVAL_MILLIS = 20L;
    private static final long ONE_LEVEL_COMMENT_TOTAL_REBUILD_LOCK_SECONDS = 2L;
    private static final long COMMENT_LIST_REBUILD_LOCK_SECONDS = 5L;


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
        ensureNoteAccessible(noteId);
        Integer pageNo = findCommentPageListReqVO.getPageNo();
        long pageSize = 10;

        // 一级评论数单独缓存；不能拿包含回复数的笔记评论总数替代。
        long count = getOneLevelCommentTotal(noteId);

        if (count == 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, 0, pageSize);
        }

        List<FindCommentItemRspVO> commentRspVOS = Lists.newArrayList();

        long offset = PageResponse.getOffset(pageNo, pageSize);

        String commentZSetKey = RedisKeyConstants.buildCommentListKey(noteId);
        boolean hasKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(commentZSetKey));

        // 若不存在且查询前 50 页热点数据，单飞抢锁重建或等待已抢锁线程建好，防止并发击穿数据库
        if (!hasKey && offset < 500) {
            rebuildCommentListZSetWithLock(commentZSetKey, noteId);
            hasKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(commentZSetKey));
        }

        if (hasKey && offset < 500) {
            Set<String> commentIds = stringRedisTemplate.<String>opsForZSet()
                    .reverseRangeByScore(commentZSetKey, -Double.MAX_VALUE, Double.MAX_VALUE, offset, pageSize);

            if (CollUtil.isNotEmpty(commentIds)) {
                List<Long> commentIdList = commentIds.stream()
                        .map(Long::valueOf)
                        .toList();

                List<String> commentIdKeys = commentIdList.stream()
                        .map(RedisKeyConstants::buildCommentDetailKey)
                        .toList();

                List<String> commentsJsonList = commentDetailCache.multiGet(commentIdKeys);

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
        CommentDO parentComment = commentDOMapper.selectByPrimaryKey(parentCommentId);
        if (parentComment == null) {
            throw new BizException(ResponseCodeEnum.PARENT_COMMENT_NOT_FOUND);
        }
        ensureNoteAccessible(parentComment.getNoteId());
        Integer pageNo = findChildCommentPageListReqVO.getPageNo();
        long pageSize = 6;

        String countCommentKey = CountKeyConstants.buildCountCommentKey(parentCommentId);
        String redisCount = stringRedisTemplate.<String, String>opsForHash()
                .get(countCommentKey, CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL);
        long count = Objects.isNull(redisCount) ? 0L : Long.parseLong(redisCount);

        if (Objects.isNull(redisCount)) {
            // 查询一级评论下子评论的总数 (直接查询 t_comment 表的 child_comment_total 字段，提升查询性能, 避免 count(*))
            List<CommentDO> countRecords = commentDOMapper.selectCommentCountByIds(List.of(parentCommentId));
            CommentDO countRecord = CollUtil.isEmpty(countRecords) ? null : countRecords.get(0);

            if (Objects.isNull(countRecord)) {
                throw new BizException(ResponseCodeEnum.PARENT_COMMENT_NOT_FOUND);
            }

            Long childTotal = countRecord.getChildCommentTotal();
            count = Objects.isNull(childTotal) ? 0L : childTotal;
            threadPoolTaskExecutor.execute(() -> syncCommentCount2Redis(countCommentKey, countRecord));
        }

        if (count == 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, 0, pageSize);
        }

        List<FindChildCommentItemRspVO> childCommentRspVOS = Lists.newArrayList();

        // 计算分页查询的偏移量 offset (需要 +1，因为最早回复的二级评论已经被展示了)
        long offset = PageResponse.getOffset(pageNo, pageSize) + 1;

        String childCommentZSetKey = RedisKeyConstants.buildChildCommentListKey(parentCommentId);
        boolean hasKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(childCommentZSetKey));

        // 若不存在且查询的是热点页（前 10 页且落在缓存窗口内），单飞抢锁重建或等待已抢锁线程建好，防止并发击穿数据库
        // 缓存窗口 = 最新 CHILD_COMMENT_LIST_MAX_SIZE 条；爆款评论翻到窗口之前的页不需要重建缓存（直接走 DB）
        if (!hasKey && offset < 6 * 10
                && offset >= Math.max(0L, count - CommentChangedLocalHandler.CHILD_COMMENT_LIST_MAX_SIZE)) {
            rebuildChildCommentListZSetWithLock(parentCommentId, childCommentZSetKey);
            hasKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(childCommentZSetKey));
        }

        // 缓存为"最新 N 条"滑动窗口，覆盖 [count - cachedSize, count) 的升序区间：
        // 仅当请求页落在该区间内才可命中 ZSET；否则（爆款评论翻到窗口之前的页）回源 DB 全量分页，
        // 保证任意 offset 可达且与 DB 全量口径一致。
        if (hasKey && offset < count) {
            Long cachedSize = stringRedisTemplate.opsForZSet().zCard(childCommentZSetKey);
            long cacheStart = Math.max(0L, count - (cachedSize == null ? 0L : cachedSize));
            if (offset >= cacheStart) {
                long rank = offset - cacheStart;
                Set<String> childCommentIds = stringRedisTemplate.<String>opsForZSet()
                        .rangeByScore(childCommentZSetKey, 0, Double.MAX_VALUE, rank, pageSize);

                if (CollUtil.isNotEmpty(childCommentIds)) {
                    List<String> childCommentIdList = Lists.newArrayList(childCommentIds);

                    List<String> commentIdKeys = childCommentIds.stream()
                            .map(RedisKeyConstants::buildCommentDetailKey)
                            .toList();

                    List<String> commentsJsonList = commentDetailCache.multiGet(commentIdKeys);

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

        String hashKey = String.valueOf(userId);

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

        String hashKey = String.valueOf(userId);

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
     * 按笔记分组批量从 KV 取评论正文，返回 contentUuid -> content
     */
    private Map<String, String> batchFindCommentContents(List<CommentDO> comments) {
        Map<String, String> contentByUuid = Maps.newHashMap();
        Map<Long, List<CommentDO>> byNote = comments.stream()
                .collect(Collectors.groupingBy(CommentDO::getNoteId));
        byNote.forEach((noteId, noteComments) -> {
            List<FindCommentContentReqDTO> reqs = Lists.newArrayList();
            noteComments.forEach(comment -> {
                if (!Boolean.TRUE.equals(comment.getIsContentEmpty())
                        && StringUtils.isNotBlank(comment.getContentUuid())
                        && comment.getCreateTime() != null) {
                    reqs.add(FindCommentContentReqDTO.builder()
                            .contentId(comment.getContentUuid())
                            .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(comment.getCreateTime()))
                            .build());
                }
            });
            if (CollUtil.isNotEmpty(reqs)) {
                List<FindCommentContentRspDTO> rsps =
                        keyValueRpcService.batchFindCommentContent(noteId, reqs);
                if (CollUtil.isNotEmpty(rsps)) {
                    rsps.forEach(rsp -> contentByUuid.put(rsp.getContentId(), rsp.getContent()));
                }
            }
        });
        return contentByUuid;
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
        List<Long> expiredCountCommentIds = Lists.newArrayList();
        List<String> commentCountKeys = notExpiredCommentIds.stream()
                .map(CountKeyConstants::buildCountCommentKey).toList();

        List<Object> results = stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                commentCountKeys.forEach(key ->
                        operations.opsForHash().entries(key));
                return null;
            }
        });

        Map<Long, Map<String, String>> commentIdAndCountMap = Maps.newHashMap();
        for (int i = 0; i < notExpiredCommentIds.size(); i++) {
            Long currCommentId = notExpiredCommentIds.get(i);
            Map<String, String> hash = (Map<String, String>) results.get(i);
            if (CollUtil.isEmpty(hash)) {
                expiredCountCommentIds.add(currCommentId);
                continue;
            }
            commentIdAndCountMap.put(currCommentId, hash);
        }

        if (CollUtil.size(expiredCountCommentIds) > 0) {
            List<CommentDO> commentDOS = commentDOMapper.selectCommentCountByIds(expiredCountCommentIds);

            commentDOS.forEach(commentDO -> {
                Integer level = commentDO.getLevel();
                Map<String, String> map = Maps.newHashMap();
                map.put(CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(commentDO.getLikeTotal()));
                // 只有一级评论需要统计子评论总数
                if (Objects.equals(level, CommentLevelEnum.ONE.getCode())) {
                    map.put(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL, String.valueOf(commentDO.getChildCommentTotal()));
                }
                commentIdAndCountMap.put(commentDO.getId(), map);
            });

            threadPoolTaskExecutor.execute(() -> {
                stringRedisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        commentDOS.forEach(commentDO -> {
                            String key = CountKeyConstants.buildCountCommentKey(commentDO.getId());
                            Integer level = commentDO.getLevel();
                            Map<String, String> fieldsMap = new HashMap<>();
                            fieldsMap.put(CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(commentDO.getLikeTotal()));
                            if (Objects.equals(level, CommentLevelEnum.ONE.getCode())) {
                                fieldsMap.put(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL, String.valueOf(commentDO.getChildCommentTotal()));
                            }
                            operations.opsForHash().putAll(key, fieldsMap);

                            long expireTime = CacheTtl.hours(1, 4);
                            operations.expire(key, expireTime, TimeUnit.SECONDS);
                        });
                        return null;
                    }
                });
            });
        }
        return commentIdAndCountMap;
    }

    /**
     * 获取子评论列表，并同步到 Redis 中
     * @param childCommentDOS
     * @param childCommentRspVOS
     */
    private void getChildCommentDataAndSync2Redis(List<CommentDO> childCommentDOS, List<FindChildCommentItemRspVO> childCommentRspVOS) {
        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        Set<Long> userIds = Sets.newHashSet();

        Long noteId = null;

        for (CommentDO childCommentDO : childCommentDOS) {
            noteId = childCommentDO.getNoteId();
            boolean isContentEmpty = childCommentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(childCommentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(childCommentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }

            userIds.add(childCommentDO.getUserId());

            Long parentId = childCommentDO.getParentId();
            Long replyCommentId = childCommentDO.getReplyCommentId();
            if (!Objects.equals(parentId, replyCommentId)) {
                userIds.add(childCommentDO.getReplyUserId());
            }
        }

        List<FindCommentContentRspDTO> findCommentContentRspDTOS =
                keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);

        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
            commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent, (a, b) -> a));
        }

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

    /**
     * 同步子评论到 Redis 中
     * @param parentCommentId
     * @param childCommentZSetKey
     */
    private void syncChildComments2Redis(Long parentCommentId, String childCommentZSetKey) {
        // 重建与增量 trim 同向：都保留最新 CHILD_COMMENT_LIST_MAX 条（取最新 5000 后倒序写入，ZSET 内保持时间升序）
        List<CommentDO> childCommentDOS = commentDOMapper.selectLatestChildCommentsByParentIdAndLimit(
                parentCommentId, (int) CommentChangedLocalHandler.CHILD_COMMENT_LIST_MAX_SIZE);
        if (CollUtil.isNotEmpty(childCommentDOS)) {
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();

                // 遍历子评论数据并批量写入 ZSet（最新优先取回，倒序后按时间升序写入）
                List<CommentDO> ascending = new ArrayList<>(childCommentDOS);
                Collections.reverse(ascending);
                for (CommentDO childCommentDO : ascending) {
                    Long commentId = childCommentDO.getId();
                    long commentTimestamp = DateUtils.localDateTime2Timestamp(childCommentDO.getCreateTime());
                    zSetOps.add(childCommentZSetKey, String.valueOf(commentId), commentTimestamp);
                }

                long randomExpiryTime = CacheTtl.hours(1, 4); // 5小时以内
                stringRedisTemplate.expire(childCommentZSetKey, randomExpiryTime, TimeUnit.SECONDS);
                return null; // 无返回值
            });
        }
    }

    /**
     * 同步评论计数到 Redis 中
     * @param countCommentKey
     * @param countRecord
     */
    private void syncCommentCount2Redis(String countCommentKey, CommentDO countRecord) {
        stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                operations.opsForHash()
                        .put(countCommentKey, CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL,
                                String.valueOf(countRecord.getChildCommentTotal()));
                operations.opsForHash()
                        .put(countCommentKey, CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(countRecord.getLikeTotal()));

                long expireTime = CacheTtl.hours(1, 4);
                operations.expire(countCommentKey, expireTime, TimeUnit.SECONDS);
                return null;
            }
        });
    }

    private long getOneLevelCommentTotal(Long noteId) {
        String version;
        try {
            version = readOneLevelCommentTotalCacheVersion(noteId);
        } catch (Exception e) {
            log.warn("Redis 不可用，一级评论总数跳过缓存并回源 MySQL，noteId={}", noteId, e);
            return queryOneLevelCommentTotal(noteId);
        }
        String key = RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId, version);
        String lockKey = RedisKeyConstants.buildOneLevelCommentTotalCacheLockKey(noteId);
        Long cached;
        try {
            cached = readOneLevelCommentTotalFromCache(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，一级评论总数缓存读取失败，回源 MySQL，noteId={}", noteId, e);
            return queryOneLevelCommentTotal(noteId);
        }
        if (cached != null) {
            return cached;
        }
        return CacheRebuildSupport.getOrRebuild(
                redissonRebuildLock(lockKey, ONE_LEVEL_COMMENT_TOTAL_REBUILD_LOCK_SECONDS),
                CACHE_REBUILD_RETRY_TIMES, CACHE_REBUILD_RETRY_INTERVAL_MILLIS,
                () -> readOneLevelCommentTotalFromCache(key),
                () -> {
                    long total = queryOneLevelCommentTotal(noteId);
                    cacheOneLevelCommentTotal(key, total);
                    return total;
                },
                () -> queryOneLevelCommentTotal(noteId));
    }

    private RebuildLock redissonRebuildLock(String lockKey, long leaseSeconds) {
        return new RebuildLock() {
            private RLock held;

            @Override
            public boolean tryLock() {
                try {
                    held = CommentServiceImpl.this.tryAcquireRebuildLock(lockKey, leaseSeconds);
                } catch (Exception e) {
                    log.warn("Redis 不可用，缓存重建锁获取失败，回源 MySQL，key={}", lockKey, e);
                    throw e;
                }
                return held != null;
            }

            @Override
            public void unlock() {
                releaseRebuildLock(held, lockKey);
            }
        };
    }

    private String readOneLevelCommentTotalCacheVersion(Long noteId) {
        String versionKey = RedisKeyConstants.buildOneLevelCommentTotalCacheVersionKey(noteId);
        String version = stringRedisTemplate.opsForValue().get(versionKey);
        if (version != null) {
            // 每次读取版本均续期，确保版本存活时间始终覆盖对应数据缓存的最大 TTL。
            stringRedisTemplate.expire(versionKey,
                    RedisKeyConstants.ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
        return version == null ? "0" : version;
    }

    private Long readOneLevelCommentTotalFromCache(String key) {
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null && StringUtils.isNumeric(cached)) {
            return Long.parseLong(cached);
        }
        return null;
    }

    private long queryOneLevelCommentTotal(Long noteId) {
        return Objects.requireNonNullElse(commentDOMapper.selectOneLevelCountByNoteId(noteId), 0L);
    }

    private void cacheOneLevelCommentTotal(String key, long total) {
        long expireSeconds = CacheTtl.minutes(10, 5);
        try {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(total), expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，一级评论总数缓存写入失败，响应将继续返回，key={}", key, e);
        }
    }

    private RLock tryAcquireRebuildLock(String lockKey, long leaseSeconds) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock == null) {
            return null;
        }
        try {
            return lock.tryLock(0, leaseSeconds, TimeUnit.SECONDS) ? lock : null;
        } catch (Exception e) {
            throw new IllegalStateException("Redis 不可用，缓存重建锁获取失败, lockKey=" + lockKey, e);
        }
    }

    private void releaseRebuildLock(RLock lock, String lockKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，缓存重建锁释放失败，key={}", lockKey, e);
        }
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

        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        List<Long> userIds = Lists.newArrayList();

        List<CommentDO> allCommentDOS = Lists.newArrayList();
        CollUtil.addAll(allCommentDOS, oneLevelCommentDOS);
        CollUtil.addAll(allCommentDOS, twoLevelCommonDOS);

        allCommentDOS.forEach(commentDO -> {
            boolean isContentEmpty = commentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(commentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(commentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }

            userIds.add(commentDO.getUserId());
        });

        List<FindCommentContentRspDTO> findCommentContentRspDTOS =
                keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);

        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
            commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent, (a, b) -> a));
        }

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

    /** 评论列表 ZSET 单飞重建：抢锁者二次检查后重建，未抢锁者轮询等待，锁在任务内释放 */
    private void rebuildCommentListZSetWithLock(String key, Long noteId) {
        String lockKey = RedisKeyConstants.buildCommentListRebuildLockKey(noteId);
        CacheRebuildSupport.rebuildIfMissing(
                redissonRebuildLock(lockKey, COMMENT_LIST_REBUILD_LOCK_SECONDS),
                CACHE_REBUILD_RETRY_TIMES, CACHE_REBUILD_RETRY_INTERVAL_MILLIS,
                () -> Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)),
                () -> syncHeatComments2Redis(key, noteId));
    }

    /** 子评论列表 ZSET 单飞重建：抢锁者二次检查后重建，未抢锁者轮询等待，锁在任务内释放 */
    private void rebuildChildCommentListZSetWithLock(Long parentCommentId, String key) {
        String lockKey = RedisKeyConstants.buildChildCommentListRebuildLockKey(parentCommentId);
        CacheRebuildSupport.rebuildIfMissing(
                redissonRebuildLock(lockKey, COMMENT_LIST_REBUILD_LOCK_SECONDS),
                CACHE_REBUILD_RETRY_TIMES, CACHE_REBUILD_RETRY_INTERVAL_MILLIS,
                () -> Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)),
                () -> syncChildComments2Redis(parentCommentId, key));
    }

    /**
     * 同步热点评论至 Redis
     * @param key
     * @param noteId
     */
    private void syncHeatComments2Redis(String key, Long noteId) {
        List<CommentDO> commentDOS = commentDOMapper.selectHeatComments(noteId);
        if (CollUtil.isNotEmpty(commentDOS)) {
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();

                for (CommentDO commentDO : commentDOS) {
                    Long commentId = commentDO.getId();
                    Double commentHeat = commentDO.getHeat();
                    zSetOps.add(key, String.valueOf(commentId), commentHeat);
                }

                long randomExpiryTime = CacheTtl.hours(1, 4); // 1~5小时
                stringRedisTemplate.expire(key, randomExpiryTime, TimeUnit.SECONDS);
                return null; // 无返回值
            });
        }
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
