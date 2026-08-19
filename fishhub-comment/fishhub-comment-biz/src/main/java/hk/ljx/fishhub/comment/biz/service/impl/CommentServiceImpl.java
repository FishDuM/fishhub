package hk.ljx.fishhub.comment.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.comment.biz.service.CommentLikeRealtimeService;
import hk.ljx.fishhub.comment.biz.service.CommentService;
import hk.ljx.fishhub.kv.dto.req.FindCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.rsp.FindCommentContentRspDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
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

    /**
     * 评论详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

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

        // 评论内容和图片不能同时为空
        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),
                "评论正文和图片不能同时为空");

        Long noteId = publishCommentReqVO.getNoteId();
        Long replyCommentId = publishCommentReqVO.getReplyCommentId();

        // 发布者 ID
        Long creatorId = LoginUserContextHolder.getUserId();

        // RPC: 调用分布式 ID 生成服务，生成评论 ID
        String commentId = distributedIdGeneratorRpcService.generateCommentId();

        // 发送 MQ
        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .commentId(Long.valueOf(commentId))
                .noteId(noteId)
                .content(content)
                .imageUrl(imageUrl)
                .replyCommentId(replyCommentId)
                .createTime(LocalDateTime.now())
                .creatorId(creatorId)
                .build();

        rocketMQTemplate.syncSend(MQConstants.TOPIC_PUBLISH_COMMENT, JsonUtils.toJsonString(publishCommentMqDTO));

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
        // 笔记 ID
        Long noteId = findCommentPageListReqVO.getNoteId();
        ensureNoteAccessible(noteId);
        // 当前页码
        Integer pageNo = findCommentPageListReqVO.getPageNo();
        // 每页展示一级评论数
        long pageSize = 10;

        // 一级评论数单独缓存；不能拿包含回复数的笔记评论总数替代。
        long count = getOneLevelCommentTotal(noteId);

        // 若评论总数为 0，则直接响应
        if (count == 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, 0, pageSize);
        }

        // 分页返参
        List<FindCommentItemRspVO> commentRspVOS = Lists.newArrayList();

        // 计算分页查询的偏移量 offset
        long offset = PageResponse.getOffset(pageNo, pageSize);

        // 评论分页缓存使用 ZSET + STRING 实现
        // 构建评论 ZSET Key
        String commentZSetKey = RedisKeyConstants.buildCommentListKey(noteId);
        // 先判断 ZSET 是否存在
        boolean hasKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(commentZSetKey));

        // 若不存在且查询前 50 页热点数据，单飞抢锁重建或等待已抢锁线程建好，防止并发击穿数据库
        if (!hasKey && offset < 500) {
            rebuildCommentListZSetWithLock(commentZSetKey, noteId);
            hasKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(commentZSetKey));
        }

        // 若 ZSET 缓存存在, 并且查询的是前 50 页的评论
        if (hasKey && offset < 500) {
            // 使用 ZRevRange 获取某篇笔记下，按热度降序排序的一级评论 ID
            Set<String> commentIds = stringRedisTemplate.<String>opsForZSet()
                    .reverseRangeByScore(commentZSetKey, -Double.MAX_VALUE, Double.MAX_VALUE, offset, pageSize);

            // 若结果不为空
            if (CollUtil.isNotEmpty(commentIds)) {
                // Set 转 List
                List<String> commentIdList = Lists.newArrayList(commentIds);

                // 先查询本地缓存
                // 新建一个集合，用于存储本地缓存中不存在的评论 ID
                List<Long> localCacheExpiredCommentIds = Lists.newArrayList();

                // 构建查询本地缓存的 Key 集合
                List<Long> localCacheKeys = commentIdList.stream()
                        .map(Long::valueOf)
                        .toList();

                // 批量查询本地缓存已存在的条目
                Map<Long, String> commentIdAndDetailJsonMap = LOCAL_CACHE.getAllPresent(localCacheKeys);
                Set<Long> hitKeys = commentIdAndDetailJsonMap.keySet();
                for (Long key : localCacheKeys) {
                    if (!hitKeys.contains(key)) {
                        localCacheExpiredCommentIds.add(key);
                    }
                }

                // 若存在已命中的本地缓存数据，转换为实体类添加到 VO 返参集合中
                if (CollUtil.isNotEmpty(commentIdAndDetailJsonMap)) {
                    for (String value : commentIdAndDetailJsonMap.values()) {
                        if (StringUtils.isBlank(value)) continue;
                        FindCommentItemRspVO commentRspVO = JsonUtils.parseObject(value, FindCommentItemRspVO.class);
                        if (commentRspVO != null) {
                            commentRspVOS.add(commentRspVO);
                        }
                    }
                }

                // 若 localCacheExpiredCommentIds 大小等于 0，说明评论详情数据都在本地缓存中，直接响应返参
                if (CollUtil.size(localCacheExpiredCommentIds) == 0) {
                    boolean missingCount = commentRspVOS.stream().anyMatch(vo -> vo.getLikeTotal() == null);
                    if (missingCount && CollUtil.isNotEmpty(commentRspVOS)) {
                        setCommentCountData(commentRspVOS, localCacheExpiredCommentIds);
                    }

                    return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
                }

                // 构建 MGET 批量查询评论详情的 Key 集合
                List<String> commentIdKeys = localCacheExpiredCommentIds.stream()
                        .map(RedisKeyConstants::buildCommentDetailKey)
                        .toList();

                // MGET 批量获取评论数据
                List<String> commentsJsonList = commentDetailCache.multiGet(commentIdKeys);

                // 可能存在部分评论不在缓存中，已经过期被删除，这些评论 ID 需要提取出来，等会查数据库
                List<Long> expiredCommentIds = Lists.newArrayList();

                for (int i = 0; i < commentsJsonList.size(); i++) {
                    String commentJson = commentsJsonList.get(i);
                    Long commentId = localCacheExpiredCommentIds.get(i);
                    if (Objects.nonNull(commentJson)) {
                        // 缓存中存在的评论 Json，直接转换为 VO 添加到返参集合中
                        FindCommentItemRspVO commentRspVO = JsonUtils.parseObject(commentJson, FindCommentItemRspVO.class);
                        commentRspVOS.add(commentRspVO);
                    } else {
                        // 评论失效，添加到失效评论列表
                        expiredCommentIds.add(commentId);
                    }
                }

                // 对于缓存中存在的评论详情, 需要再次查询其计数数据
                if (CollUtil.isNotEmpty(commentRspVOS)) {
                    setCommentCountData(commentRspVOS, expiredCommentIds);
                }

                // 对于不存在的一级评论，需要批量从数据库中查询，并添加到 commentRspVOS 中
                if (CollUtil.isNotEmpty(expiredCommentIds)) {
                    List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(expiredCommentIds);
                    getCommentDataAndSync2Redis(commentDOS, noteId, commentRspVOS);
                }
            }

            // 按热度值进行降序排列
            commentRspVOS = commentRspVOS.stream()
                    .sorted(Comparator.comparing(FindCommentItemRspVO::getHeat).reversed())
                    .collect(Collectors.toList());

            // 异步将评论详情，同步到本地缓存
            syncCommentDetail2LocalCache(commentRspVOS);

            return PageResponse.success(commentRspVOS, pageNo, count, pageSize);
        }

        // 缓存中没有，则查询数据库
        // 查询一级评论
        List<CommentDO> oneLevelCommentDOS = commentDOMapper.selectPageList(noteId, offset, pageSize);
        getCommentDataAndSync2Redis(oneLevelCommentDOS, noteId, commentRspVOS);

        // 异步将评论详情，同步到本地缓存
        syncCommentDetail2LocalCache(commentRspVOS);

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
        // 父评论 ID
        Long parentCommentId = findChildCommentPageListReqVO.getParentCommentId();
        CommentDO parentComment = commentDOMapper.selectByPrimaryKey(parentCommentId);
        if (parentComment == null) {
            throw new BizException(ResponseCodeEnum.PARENT_COMMENT_NOT_FOUND);
        }
        ensureNoteAccessible(parentComment.getNoteId());
        // 当前页码
        Integer pageNo = findChildCommentPageListReqVO.getPageNo();
        // 每页展示的二级评论数 (飞鱼社区 APP 中是一次查询 6 条)
        long pageSize = 6;

        // 先从缓存中查
        String countCommentKey = CountKeyConstants.buildCountCommentKey(parentCommentId);
        // 子评论总数
        String redisCount = stringRedisTemplate.<String, String>opsForHash()
                .get(countCommentKey, CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL);
        long count = Objects.isNull(redisCount) ? 0L : Long.parseLong(redisCount);

        // 若缓存不存在，走数据库查询
        if (Objects.isNull(redisCount)) {
            // 查询一级评论下子评论的总数 (直接查询 t_comment 表的 child_comment_total 字段，提升查询性能, 避免 count(*))
            List<CommentDO> countRecords = commentDOMapper.selectCommentCountByIds(List.of(parentCommentId));
            CommentDO countRecord = CollUtil.isEmpty(countRecords) ? null : countRecords.get(0);

            // 若数据库中也不存在，则抛出业务异常
            if (Objects.isNull(countRecord)) {
                throw new BizException(ResponseCodeEnum.PARENT_COMMENT_NOT_FOUND);
            }

            count = countRecord.getChildCommentTotal();
            threadPoolTaskExecutor.execute(() -> syncCommentCount2Redis(countCommentKey, countRecord));
        }

        // 若子评论总数为 0，直接返参
        if (count == 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, 0, pageSize);
        }

        // 分页返参 VO
        List<FindChildCommentItemRspVO> childCommentRspVOS = Lists.newArrayList();

        // 计算分页查询的偏移量 offset (需要 +1，因为最早回复的二级评论已经被展示了)
        long offset = PageResponse.getOffset(pageNo, pageSize) + 1;

        // 子评论分页缓存使用 ZSET + STRING 实现
        // 构建子评论 ZSET Key
        String childCommentZSetKey = RedisKeyConstants.buildChildCommentListKey(parentCommentId);
        // 先判断 ZSET 是否存在
        boolean hasKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(childCommentZSetKey));

        // 若不存在且查询前 10 页热点数据，单飞抢锁重建或等待已抢锁线程建好，防止并发击穿数据库
        if (!hasKey && offset < 6 * 10) {
            rebuildChildCommentListZSetWithLock(parentCommentId, childCommentZSetKey);
            hasKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(childCommentZSetKey));
        }

        // 若子评论 ZSET 缓存存在, 并且查询的是前 10 页的子评论
        if (hasKey && offset < 6*10) {
            // 使用 ZRevRange 获取某个一级评论下的子评论，按回复时间升序排列
            Set<String> childCommentIds = stringRedisTemplate.<String>opsForZSet()
                    .rangeByScore(childCommentZSetKey, 0, Double.MAX_VALUE, offset, pageSize);

            // 若结果不为空
            if (CollUtil.isNotEmpty(childCommentIds)) {
                // Set 转 List
                List<String> childCommentIdList = Lists.newArrayList(childCommentIds);

                // 构建 MGET 批量查询子评论详情的 Key 集合
                List<String> commentIdKeys = childCommentIds.stream()
                        .map(RedisKeyConstants::buildCommentDetailKey)
                        .toList();

                // MGET 批量获取评论数据
                List<String> commentsJsonList = commentDetailCache.multiGet(commentIdKeys);

                // 可能存在部分评论不在缓存中，已经过期被删除，这些评论 ID 需要提取出来，等会查数据库
                List<Long> expiredChildCommentIds = Lists.newArrayList();

                for (int i = 0; i < commentsJsonList.size(); i++) {
                    String commentJson = commentsJsonList.get(i);
                    Long commentId = Long.valueOf(childCommentIdList.get(i));
                    if (Objects.nonNull(commentJson)) {
                        // 缓存中存在的评论 Json，直接转换为 VO 添加到返参集合中
                        FindChildCommentItemRspVO childCommentRspVO = JsonUtils.parseObject(commentJson, FindChildCommentItemRspVO.class);
                        childCommentRspVOS.add(childCommentRspVO);
                    } else {
                        // 评论失效，添加到失效评论列表
                        expiredChildCommentIds.add(commentId);
                    }
                }

                // 对于缓存中存在的子评论, 需要再次查询 Hash, 获取其计数数据
                if (CollUtil.isNotEmpty(childCommentRspVOS)) {
                    setChildCommentCountData(childCommentRspVOS, expiredChildCommentIds);
                }

                // 对于不存在的子评论，需要批量从数据库中查询，并添加到 commentRspVOS 中
                if (CollUtil.isNotEmpty(expiredChildCommentIds)) {
                    List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(expiredChildCommentIds);
                    getChildCommentDataAndSync2Redis(commentDOS, childCommentRspVOS);
                }

                // 按评论 ID 升序排列（等同于按回复时间升序）
                childCommentRspVOS = childCommentRspVOS.stream()
                        .sorted(Comparator.comparing(FindChildCommentItemRspVO::getCommentId))
                        .collect(Collectors.toList());

                return PageResponse.success(childCommentRspVOS, pageNo, count, pageSize);
            }

        }

        // 分页查询子评论
        List<CommentDO> childCommentDOS = commentDOMapper.selectChildPageList(parentCommentId, offset, pageSize);

        getChildCommentDataAndSync2Redis(childCommentDOS, childCommentRspVOS);

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
        // 校验是否已点赞
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

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (RuntimeException e) {
            throw new IllegalStateException("评论点赞消息发送失败", e);
        }

        // 实时更新点赞状态及计数
        commentLikeRealtimeService.markLiked(userId, commentId);

        return Response.success();
    }

    /**
     * 取消评论点赞
     *
     * @param unLikeCommentReqVO
     * @return
     */
    @Override
    public Response<?> unlikeComment(UnLikeCommentReqVO unLikeCommentReqVO) {
        Long commentId = unLikeCommentReqVO.getCommentId();

        Long userId = LoginUserContextHolder.getUserId();
        // 校验是否已点赞
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

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (RuntimeException e) {
            throw new IllegalStateException("评论取消点赞消息发送失败", e);
        }

        // 实时更新点赞状态及计数
        commentLikeRealtimeService.markUnliked(userId, commentId);

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

        // 过滤不可访问笔记的评论
        List<Long> noteIds = comments.stream().map(CommentDO::getNoteId).distinct().toList();
        Set<Long> accessibleNoteIds = new HashSet<>(noteRpcService.findAccessibleNoteIds(noteIds));
        List<CommentDO> accessibleComments = comments.stream()
                .filter(comment -> accessibleNoteIds.contains(comment.getNoteId()))
                .toList();
        if (CollUtil.isEmpty(accessibleComments)) {
            return PageResponse.success(Collections.emptyList(), pageNo, page.total());
        }

        // 批量获取评论正文
        Map<String, String> contentByUuid = batchFindCommentContents(accessibleComments);

        // 批量取作者信息
        List<Long> authorIds = accessibleComments.stream()
                .map(CommentDO::getUserId)
                .distinct()
                .toList();
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap;
        if (CollUtil.isNotEmpty(authorIds)) {
            List<FindUserByIdRspDTO> users = userClient.findByIds(authorIds);
            userIdAndDTOMap = CollUtil.isNotEmpty(users)
                    ? users.stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto))
                    : Collections.emptyMap();
        } else {
            userIdAndDTOMap = Collections.emptyMap();
        }

        List<FindLikedCommentItemRspVO> items = accessibleComments.stream().map(comment -> {
            FindUserByIdRspDTO author = userIdAndDTOMap.get(comment.getUserId());
            String content = null;
            if (!Boolean.TRUE.equals(comment.getIsContentEmpty()) && StringUtils.isNotBlank(comment.getContentUuid())) {
                content = contentByUuid.get(comment.getContentUuid());
            }
            return FindLikedCommentItemRspVO.builder()
                    .commentId(comment.getId())
                    .noteId(comment.getNoteId())
                    .userId(comment.getUserId())
                    .avatar(author == null ? null : author.getAvatar())
                    .nickname(author == null ? null : author.getNickName())
                    .content(content)
                    .imageUrl(comment.getImageUrl())
                    .likeTime(DateUtils.formatRelativeTime(comment.getCreateTime()))
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
        // 被删除的评论 ID
        Long commentId = deleteCommentReqVO.getCommentId();

        // 1. 校验评论是否存在
        CommentDO commentDO = commentDOMapper.selectByPrimaryKey(commentId);

        if (Objects.isNull(commentDO)) {
            throw new BizException(ResponseCodeEnum.COMMENT_NOT_FOUND);
        }
        ensureNoteAccessible(commentDO.getNoteId());

        // 2. 校验是否有权限删除
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, commentDO.getUserId())) {
            throw new BizException(ResponseCodeEnum.COMMENT_CANT_OPERATE);
        }

        // 整个删除树由消费端完整处理；Broker 未确认前不删除主评论。
        // 构建消息对象，并将 DO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(commentDO))
                .build();

        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_COMMENT, message);

        return Response.success();
    }

    /**
     * 删除本地评论缓存
     *
     * @param commentId
     */
    @Override
    public void deleteCommentLocalCache(Long commentId) {
        LOCAL_CACHE.invalidate(commentId);
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
        // 准备从评论 Hash 中查询计数 (被点赞数)
        // 缓存中存在的子评论 ID
        List<Long> notExpiredCommentIds = Lists.newArrayList();

        // 遍历从缓存中解析出的 VO 集合，提取二级评论 ID
        commentRspVOS.forEach(commentRspVO -> {
            Long childCommentId = commentRspVO.getCommentId();
            notExpiredCommentIds.add(childCommentId);
        });

        // 从 Redis 中查询评论计数 Hash 数据
        Map<Long, Map<String, String>> commentIdAndCountMap = getCommentCountDataAndSync2RedisHash(notExpiredCommentIds);

        // 遍历 VO, 设置对应子评论的点赞数
        for (FindChildCommentItemRspVO commentRspVO : commentRspVOS) {
            // 评论 ID
            Long commentId = commentRspVO.getCommentId();

            // 若当前这条评论是从数据库中查询出来的, 则无需设置点赞数，以数据库查询出来的为主
            if (CollUtil.isNotEmpty(expiredCommentIds)
                    && expiredCommentIds.contains(commentId)) {
                continue;
            }

            // 设置子评论的点赞数
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
        // 已失效的 Hash 评论 ID
        List<Long> expiredCountCommentIds = Lists.newArrayList();
        // 构建需要查询的 Hash Key 集合
        List<String> commentCountKeys = notExpiredCommentIds.stream()
                .map(CountKeyConstants::buildCountCommentKey).toList();

        // 使用 RedisTemplate 执行管道批量操作
        List<Object> results = stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                // 遍历需要查询的评论计数的 Hash 键集合
                commentCountKeys.forEach(key ->
                        // 在管道中执行 Redis 的 hash.entries 操作
                        // 此操作会获取指定 Hash 键中所有的字段和值
                        operations.opsForHash().entries(key));
                return null;
            }
        });

        // 评论 ID - 计数数据字典
        Map<Long, Map<String, String>> commentIdAndCountMap = Maps.newHashMap();
        // 遍历未过期的评论 ID 集合
        for (int i = 0; i < notExpiredCommentIds.size(); i++) {
            // 当前评论 ID
            Long currCommentId = notExpiredCommentIds.get(i);
            // 从缓存查询结果中，获取对应 Hash
            Map<String, String> hash = (Map<String, String>) results.get(i);
            // 若 Hash 结果为空，说明缓存中不存在，添加到 expiredCountCommentIds 中，保存一下
            if (CollUtil.isEmpty(hash)) {
                expiredCountCommentIds.add(currCommentId);
                continue;
            }
            // 若存在，则将数据添加到 commentIdAndCountMap 中，方便后续读取
            commentIdAndCountMap.put(currCommentId, hash);
        }

        // 若已过期的计数评论 ID 集合大于 0，说明部分计数数据不在 Redis 缓存中
        // 需要查询数据库，并将这部分的评论计数 Hash 同步到 Redis 中
        if (CollUtil.size(expiredCountCommentIds) > 0) {
            // 查询数据库
            List<CommentDO> commentDOS = commentDOMapper.selectCommentCountByIds(expiredCountCommentIds);

            commentDOS.forEach(commentDO -> {
                Integer level = commentDO.getLevel();
                Map<String, String> map = Maps.newHashMap();
                map.put(CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(commentDO.getLikeTotal()));
                // 只有一级评论需要统计子评论总数
                if (Objects.equals(level, CommentLevelEnum.ONE.getCode())) {
                    map.put(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL, String.valueOf(commentDO.getChildCommentTotal()));
                }
                // 统一添加到 commentIdAndCountMap 字典中，方便后续查询
                commentIdAndCountMap.put(commentDO.getId(), map);
            });

            // 异步同步到 Redis 中
            threadPoolTaskExecutor.execute(() -> {
                stringRedisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        commentDOS.forEach(commentDO -> {
                            // 构建 Hash Key
                            String key = CountKeyConstants.buildCountCommentKey(commentDO.getId());
                            // 评论级别
                            Integer level = commentDO.getLevel();
                            // 设置 Field 数据
                            Map<String, String> fieldsMap = Objects.equals(level, CommentLevelEnum.ONE.getCode()) ?
                                    Map.of(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL, String.valueOf(commentDO.getChildCommentTotal()),
                                            CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(commentDO.getLikeTotal())) : Map.of(CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(commentDO.getLikeTotal()));
                            // 添加 Hash 数据
                            operations.opsForHash().putAll(key, fieldsMap);

                            // 设置随机过期时间 (5小时以内)
                            long expireTime = CacheTtl.hours(0, 5);
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
        // 调用 KV 服务需要的入参
        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        // 调用用户服务的入参
        Set<Long> userIds = Sets.newHashSet();

        // 归属的笔记 ID
        Long noteId = null;

        // 循环提取 RPC 调用需要的入参数据
        for (CommentDO childCommentDO : childCommentDOS) {
            noteId = childCommentDO.getNoteId();
            // 构建调用 KV 服务批量查询评论内容的入参
            boolean isContentEmpty = childCommentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(childCommentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(childCommentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }

            // 构建调用用户服务批量查询用户信息的入参 (包含评论发布者、回复的目标用户)
            userIds.add(childCommentDO.getUserId());

            Long parentId = childCommentDO.getParentId();
            Long replyCommentId = childCommentDO.getReplyCommentId();
            if (!Objects.equals(parentId, replyCommentId)) {
                userIds.add(childCommentDO.getReplyUserId());
            }
        }

        // RPC: 调用 KV 服务，批量获取评论内容
        List<FindCommentContentRspDTO> findCommentContentRspDTOS =
                keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);

        // DTO 集合转 Map, 方便后续拼装数据
        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
            commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent));
        }

        // RPC: 调用用户服务，批量获取用户信息（头像、昵称等）
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userClient.findByIds(userIds.stream().toList());

        // DTO 集合转 Map, 方便后续拼装数据
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = Collections.emptyMap();
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            userIdAndDTOMap = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto));
        }

        // DO 转 VO
        for (CommentDO childCommentDO : childCommentDOS) {
            // 构建 VO 实体类
            Long userId = childCommentDO.getUserId();
            FindChildCommentItemRspVO childCommentRspVO = FindChildCommentItemRspVO.builder()
                    .userId(userId)
                    .commentId(childCommentDO.getId())
                    .imageUrl(childCommentDO.getImageUrl())
                    .createTime(DateUtils.formatRelativeTime(childCommentDO.getCreateTime()))
                    .likeTotal(childCommentDO.getLikeTotal())
                    .build();

            // 填充用户信息(包括评论发布者、回复的用户)
            if (CollUtil.isNotEmpty(userIdAndDTOMap)) {
                FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(userId);
                // 评论发布者用户信息(头像、昵称)
                if (Objects.nonNull(findUserByIdRspDTO)) {
                    childCommentRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                    childCommentRspVO.setNickname(findUserByIdRspDTO.getNickName());
                }

                // 评论回复的哪个
                Long replyCommentId = childCommentDO.getReplyCommentId();
                Long parentId = childCommentDO.getParentId();

                if (Objects.nonNull(replyCommentId)
                        && !Objects.equals(replyCommentId, parentId)) {
                    Long replyUserId = childCommentDO.getReplyUserId();
                    FindUserByIdRspDTO replyUser = userIdAndDTOMap.get(replyUserId);
                    if (replyUser != null) {
                        childCommentRspVO.setReplyUserName(replyUser.getNickName());
                        childCommentRspVO.setReplyUserId(replyUser.getId());
                    }
                }
            }

            // 评论内容
            if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
                String contentUuid = childCommentDO.getContentUuid();
                if (StringUtils.isNotBlank(contentUuid)) {
                    childCommentRspVO.setContent(commentUuidAndContentMap.get(contentUuid));
                }
            }

            childCommentRspVOS.add(childCommentRspVO);
        }

        // 异步将笔记详情，同步到 Redis 中
        threadPoolTaskExecutor.execute(() -> {
            // 准备批量写入的数据
            Map<String, String> data = Maps.newHashMap();
            childCommentRspVOS.forEach(commentRspVO -> {
                // 评论 ID
                Long commentId = commentRspVO.getCommentId();
                // 构建 Key
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
        List<CommentDO> childCommentDOS = commentDOMapper.selectChildCommentsByParentIdAndLimit(parentCommentId, 6*10);
        if (CollUtil.isNotEmpty(childCommentDOS)) {
            // 使用 Redis Pipeline 提升写入性能
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();

                // 遍历子评论数据并批量写入 ZSet
                for (CommentDO childCommentDO : childCommentDOS) {
                    Long commentId = childCommentDO.getId();
                    // create_time 转时间戳
                    long commentTimestamp = DateUtils.localDateTime2Timestamp(childCommentDO.getCreateTime());
                    zSetOps.add(childCommentZSetKey, String.valueOf(commentId), commentTimestamp);
                }

                // 设置随机过期时间，（保底1小时 + 随机时间），单位：秒
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
                // 同步 hash 数据
                operations.opsForHash()
                        .put(countCommentKey, CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL,
                                String.valueOf(countRecord.getChildCommentTotal()));
                operations.opsForHash()
                        .put(countCommentKey, CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(countRecord.getLikeTotal()));

                // 随机过期时间 (保底1小时 + 随机时间)，单位：秒
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

        RLock lock;
        try {
            lock = tryAcquireRebuildLock(lockKey, ONE_LEVEL_COMMENT_TOTAL_REBUILD_LOCK_SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，一级评论总数重建锁获取失败，回源 MySQL，noteId={}", noteId, e);
            return queryOneLevelCommentTotal(noteId);
        }
        if (lock == null) {
            try {
                cached = waitForOneLevelCommentTotal(key);
            } catch (Exception e) {
                log.warn("Redis 不可用，一级评论总数缓存重试失败，回源 MySQL，noteId={}", noteId, e);
                return queryOneLevelCommentTotal(noteId);
            }
            return cached == null ? queryOneLevelCommentTotal(noteId) : cached;
        }
        try {
            try {
                cached = readOneLevelCommentTotalFromCache(key);
            } catch (Exception e) {
                log.warn("Redis 不可用，一级评论总数二次缓存读取失败，回源 MySQL，noteId={}", noteId, e);
                return queryOneLevelCommentTotal(noteId);
            }
            if (cached != null) {
                return cached;
            }
            long total = queryOneLevelCommentTotal(noteId);
            cacheOneLevelCommentTotal(key, total);
            return total;
        } finally {
            releaseRebuildLock(lock, lockKey);
        }
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

    private Long waitForOneLevelCommentTotal(String key) {
        for (int i = 0; i < CACHE_REBUILD_RETRY_TIMES; i++) {
            sleepBeforeCacheRetry();
            Long cached = readOneLevelCommentTotalFromCache(key);
            if (cached != null) {
                return cached;
            }
        }
        return null;
    }

    private RLock tryAcquireRebuildLock(String lockKey, long leaseSeconds) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock == null) {
            return null;
        }
        try {
            return lock.tryLock(0, leaseSeconds, TimeUnit.SECONDS) ? lock : null;
        } catch (Exception e) {
            throw new IllegalStateException("Redis 不可用，一级评论总数重建锁获取失败, lockKey=" + lockKey, e);
        }
    }

    private void releaseRebuildLock(RLock lock, String lockKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，一级评论总数重建锁释放失败，key={}", lockKey, e);
        }
    }

    private void sleepBeforeCacheRetry() {
        try {
            Thread.sleep(CACHE_REBUILD_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        // 准备从评论 Hash 中查询计数 (子评论总数、被点赞数)
        // 缓存中存在的评论 ID
        List<Long> notExpiredCommentIds = Lists.newArrayList();

        // 遍历从缓存中解析出的 VO 集合，提取一级、二级评论 ID
        commentRspVOS.forEach(commentRspVO -> {
            Long oneLevelCommentId = commentRspVO.getCommentId();
            notExpiredCommentIds.add(oneLevelCommentId);
            FindCommentItemRspVO firstCommentVO = commentRspVO.getFirstReplyComment();
            if (Objects.nonNull(firstCommentVO)) {
                notExpiredCommentIds.add(firstCommentVO.getCommentId());
            }
        });

        // 已失效的 Hash 评论 ID
        Map<Long, Map<String, String>> commentIdAndCountMap = getCommentCountDataAndSync2RedisHash(notExpiredCommentIds);

        // 遍历 VO, 设置对应评论的二级评论数、点赞数
        for (FindCommentItemRspVO commentRspVO : commentRspVOS) {
            // 评论 ID
            Long commentId = commentRspVO.getCommentId();

            // 若当前这条评论是从数据库中查询出来的, 则无需设置二级评论数、点赞数，以数据库查询出来的为主
            if (CollUtil.isNotEmpty(expiredCommentIds)
                    && expiredCommentIds.contains(commentId)) {
                continue;
            }

            // 设置一级评论的子评论总数、点赞数
            Map<String, String> hash = commentIdAndCountMap.get(commentId);
            if (CollUtil.isNotEmpty(hash)) {
                String likeTotalObj = hash.get(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL);
                Long childCommentTotal = Objects.isNull(likeTotalObj) ? 0 : Long.parseLong(likeTotalObj);
                String likeTotalFieldObj = hash.get(CountKeyConstants.FIELD_LIKE_TOTAL);
                Long likeTotal = Objects.isNull(likeTotalFieldObj) ? 0 : Long.parseLong(likeTotalFieldObj);
                commentRspVO.setChildCommentTotal(childCommentTotal);
                commentRspVO.setLikeTotal(likeTotal);
                // 最初回复的二级评论
                FindCommentItemRspVO firstCommentVO = commentRspVO.getFirstReplyComment();
                if (Objects.nonNull(firstCommentVO)) {
                    Long firstCommentId = firstCommentVO.getCommentId();
                    Map<String, String> firstCommentHash = commentIdAndCountMap.get(firstCommentId);
                    if (CollUtil.isNotEmpty(firstCommentHash)) {
                        Long firstCommentLikeTotal = Long.valueOf(firstCommentHash.get(CountKeyConstants.FIELD_LIKE_TOTAL));
                        firstCommentVO.setLikeTotal(firstCommentLikeTotal);
                    }
                }
            }
        }
    }


    /**
     * 同步评论详情到本地缓存中
     *
     * @param commentRspVOS
     */
    private void syncCommentDetail2LocalCache(List<FindCommentItemRspVO> commentRspVOS) {
        // 开启一个异步线程
        threadPoolTaskExecutor.execute(() -> {
            // 构建缓存所需的键值
            Map<Long, String> localCacheData = Maps.newHashMap();
            commentRspVOS.forEach(commentRspVO -> {
                Long commentId = commentRspVO.getCommentId();
                localCacheData.put(commentId, JsonUtils.toJsonString(commentRspVO));
            });

            // 批量写入本地缓存
            LOCAL_CACHE.putAll(localCacheData);
        });
    }

    /**
     * 获取全部评论数据，并将评论详情同步到 Redis 中
     * @param oneLevelCommentDOS
     * @param noteId
     * @param commentRspVOS
     */
    private void getCommentDataAndSync2Redis(List<CommentDO> oneLevelCommentDOS, Long noteId, List<FindCommentItemRspVO> commentRspVOS) {
        // 过滤出所有最早回复的二级评论 ID
        List<Long> twoLevelCommentIds = oneLevelCommentDOS.stream()
                .map(CommentDO::getFirstReplyCommentId)
                .filter(firstReplyCommentId -> firstReplyCommentId != null && firstReplyCommentId != 0)
                .toList();

        // 查询二级评论
        Map<Long, CommentDO> commentIdAndDOMap = null;
        List<CommentDO> twoLevelCommonDOS = null;
        if (CollUtil.isNotEmpty(twoLevelCommentIds)) {
            twoLevelCommonDOS = commentDOMapper.selectTwoLevelCommentByIds(twoLevelCommentIds);

            // 转 Map 集合，方便后续拼装数据
            commentIdAndDOMap = twoLevelCommonDOS.stream()
                    .collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO));
        }

        // 调用 KV 服务需要的入参
        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        // 调用用户服务的入参
        List<Long> userIds = Lists.newArrayList();

        // 将一级评论和二级评论合并到一起
        List<CommentDO> allCommentDOS = Lists.newArrayList();
        CollUtil.addAll(allCommentDOS, oneLevelCommentDOS);
        CollUtil.addAll(allCommentDOS, twoLevelCommonDOS);

        // 循环提取 RPC 调用需要的入参数据
        allCommentDOS.forEach(commentDO -> {
            // 构建调用 KV 服务批量查询评论内容的入参
            boolean isContentEmpty = commentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(commentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(commentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }

            // 构建调用用户服务批量查询用户信息的入参
            userIds.add(commentDO.getUserId());
        });

        // RPC: 调用 KV 服务，批量获取评论内容
        List<FindCommentContentRspDTO> findCommentContentRspDTOS =
                keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);

        // DTO 集合转 Map, 方便后续拼装数据
        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRspDTOS)) {
            commentUuidAndContentMap = findCommentContentRspDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRspDTO::getContentId, FindCommentContentRspDTO::getContent));
        }

        // RPC: 调用用户服务，批量获取用户信息（头像、昵称等）
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userClient.findByIds(userIds);

        // DTO 集合转 Map, 方便后续拼装数据
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = Collections.emptyMap();
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            userIdAndDTOMap = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto));
        }

        // DO 转 VO, 组合拼装一二级评论数据
        for (CommentDO commentDO : oneLevelCommentDOS) {
            // 一级评论
            Long userId = commentDO.getUserId();
            FindCommentItemRspVO oneLevelCommentRspVO = FindCommentItemRspVO.builder()
                    .userId(userId)
                    .commentId(commentDO.getId())
                    .imageUrl(commentDO.getImageUrl())
                    .createTime(DateUtils.formatRelativeTime(commentDO.getCreateTime()))
                    .likeTotal(commentDO.getLikeTotal())
                    .childCommentTotal(commentDO.getChildCommentTotal())
                    .heat(commentDO.getHeat())
                    .build();

            // 用户信息
            setUserInfo(userIdAndDTOMap, userId, oneLevelCommentRspVO);
            // 笔记内容
            setCommentContent(commentUuidAndContentMap, commentDO, oneLevelCommentRspVO);


            // 二级评论
            Long firstReplyCommentId = commentDO.getFirstReplyCommentId();
            if (CollUtil.isNotEmpty(commentIdAndDOMap)) {
                CommentDO firstReplyCommentDO = commentIdAndDOMap.get(firstReplyCommentId);
                if (Objects.nonNull(firstReplyCommentDO)) {
                    Long firstReplyCommentUserId = firstReplyCommentDO.getUserId();
                    FindCommentItemRspVO firstReplyCommentRspVO = FindCommentItemRspVO.builder()
                            .userId(firstReplyCommentDO.getUserId())
                            .commentId(firstReplyCommentDO.getId())
                            .imageUrl(firstReplyCommentDO.getImageUrl())
                            .createTime(DateUtils.formatRelativeTime(firstReplyCommentDO.getCreateTime()))
                            .likeTotal(firstReplyCommentDO.getLikeTotal())
                            .heat(firstReplyCommentDO.getHeat())
                            .build();

                    setUserInfo(userIdAndDTOMap, firstReplyCommentUserId, firstReplyCommentRspVO);

                    // 用户信息
                    oneLevelCommentRspVO.setFirstReplyComment(firstReplyCommentRspVO);
                    // 笔记内容
                    setCommentContent(commentUuidAndContentMap, firstReplyCommentDO, firstReplyCommentRspVO);
                }
            }
            commentRspVOS.add(oneLevelCommentRspVO);
        }

        // 异步将笔记详情，同步到 Redis 中
        threadPoolTaskExecutor.execute(() -> {
            // 准备批量写入的数据
            Map<String, String> data = Maps.newHashMap();
            commentRspVOS.forEach(commentRspVO -> {
                // 评论 ID
                Long commentId = commentRspVO.getCommentId();
                // 构建 Key
                String key = RedisKeyConstants.buildCommentDetailKey(commentId);
                data.put(key, JsonUtils.toJsonString(commentRspVO));
            });

            // 使用 Redis Pipeline 提升写入性能
            commentDetailCache.putAll(data);
        });
    }

    /** 评论列表 ZSET 单飞重建：抢锁者二次检查后重建，未抢锁者轮询等待，锁在任务内释放 */
    private void rebuildCommentListZSetWithLock(String key, Long noteId) {
        String lockKey = RedisKeyConstants.buildCommentListRebuildLockKey(noteId);
        RLock lock;
        try {
            lock = tryAcquireRebuildLock(lockKey, COMMENT_LIST_REBUILD_LOCK_SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，评论列表 ZSET 重建锁获取失败，跳过重建，noteId={}", noteId, e);
            return;
        }
        if (lock == null) {
            try {
                waitForCommentListZSet(key);
            } catch (Exception e) {
                log.warn("Redis 不可用，评论列表 ZSET 重建等待失败，noteId={}", noteId, e);
            }
            return;
        }
        try {
            // 二次检查：抢锁期间其他节点可能已完成重建
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            syncHeatComments2Redis(key, noteId);
        } finally {
            releaseRebuildLock(lock, lockKey);
        }
    }

    private void waitForCommentListZSet(String key) {
        for (int i = 0; i < CACHE_REBUILD_RETRY_TIMES; i++) {
            sleepBeforeCacheRetry();
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
        }
    }

    /** 子评论列表 ZSET 单飞重建：抢锁者二次检查后重建，未抢锁者轮询等待，锁在任务内释放 */
    private void rebuildChildCommentListZSetWithLock(Long parentCommentId, String key) {
        String lockKey = RedisKeyConstants.buildChildCommentListRebuildLockKey(parentCommentId);
        RLock lock;
        try {
            lock = tryAcquireRebuildLock(lockKey, COMMENT_LIST_REBUILD_LOCK_SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，子评论列表 ZSET 重建锁获取失败，跳过重建，parentCommentId={}", parentCommentId, e);
            return;
        }
        if (lock == null) {
            try {
                waitForCommentListZSet(key);
            } catch (Exception e) {
                log.warn("Redis 不可用，子评论列表 ZSET 重建等待失败，parentCommentId={}", parentCommentId, e);
            }
            return;
        }
        try {
            // 二次检查：抢锁期间其他节点可能已完成重建
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            syncChildComments2Redis(parentCommentId, key);
        } finally {
            releaseRebuildLock(lock, lockKey);
        }
    }

    /**
     * 同步热点评论至 Redis
     * @param key
     * @param noteId
     */
    private void syncHeatComments2Redis(String key, Long noteId) {
        List<CommentDO> commentDOS = commentDOMapper.selectHeatComments(noteId);
        if (CollUtil.isNotEmpty(commentDOS)) {
            // 使用 Redis Pipeline 提升写入性能
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();

                // 遍历评论数据并批量写入 ZSet
                for (CommentDO commentDO : commentDOS) {
                    Long commentId = commentDO.getId();
                    Double commentHeat = commentDO.getHeat();
                    zSetOps.add(key, String.valueOf(commentId), commentHeat);
                }

                // 设置随机过期时间，单位：秒
                long randomExpiryTime = CacheTtl.hours(0, 5); // 5小时以内
                stringRedisTemplate.expire(key, randomExpiryTime, TimeUnit.SECONDS);
                return null; // 无返回值
            });
        }
    }

    /**
     * 设置评论内容
     * @param commentUuidAndContentMap
     * @param commentDO1
     * @param firstReplyCommentRspVO
     */
    private static void setCommentContent(Map<String, String> commentUuidAndContentMap, CommentDO commentDO1, FindCommentItemRspVO firstReplyCommentRspVO) {
        if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
            String contentUuid = commentDO1.getContentUuid();
            if (StringUtils.isNotBlank(contentUuid)) {
                firstReplyCommentRspVO.setContent(commentUuidAndContentMap.get(contentUuid));
            }
        }
    }

    /**
     * 设置用户信息
     * @param userIdAndDTOMap
     * @param userId
     * @param oneLevelCommentRspVO
     */
    private static void setUserInfo(Map<Long, FindUserByIdRspDTO> userIdAndDTOMap, Long userId, FindCommentItemRspVO oneLevelCommentRspVO) {
        if (CollUtil.isNotEmpty(userIdAndDTOMap)) {
            FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(userId);
            if (Objects.nonNull(findUserByIdRspDTO)) {
                oneLevelCommentRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                oneLevelCommentRspVO.setNickname(findUserByIdRspDTO.getNickName());
            }
        }
    }


}
