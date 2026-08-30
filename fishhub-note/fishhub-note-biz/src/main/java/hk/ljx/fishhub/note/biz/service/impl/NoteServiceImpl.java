package hk.ljx.fishhub.note.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Lists;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.common.util.SafeRedisUtil;
import hk.ljx.framework.common.util.NumberUtils;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.ChannelDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.ChannelDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.TopicDOMapper;
import hk.ljx.fishhub.note.biz.enums.*;
import hk.ljx.fishhub.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import hk.ljx.fishhub.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import hk.ljx.fishhub.note.api.NoteChangedEventMqDTO;
import hk.ljx.fishhub.note.api.NoteContentTaskMqDTO;
import hk.ljx.fishhub.note.biz.model.bo.NoteAccessSnapshot;
import hk.ljx.fishhub.note.biz.model.vo.*;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.note.biz.rpc.DistributedIdGeneratorRpcService;
import hk.ljx.fishhub.note.biz.rpc.OssRpcService;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.support.RocketMqHelper;
import hk.ljx.fishhub.note.biz.service.NoteService;
import hk.ljx.fishhub.note.biz.service.NotePersistenceService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.note.biz.service.UserNoteListService;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import hk.ljx.framework.common.util.RedisScriptHelper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


import hk.ljx.fishhub.note.biz.domain.repository.NoteContentRepository;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteContentDO;

@Service
@Slf4j
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {



    private final NoteDOMapper noteDOMapper;
    private final TopicDOMapper topicDOMapper;
    private final ChannelDOMapper channelDOMapper;
    private final DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    private final NoteContentRepository noteContentRepository;
    private final UserClient userClient;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final StringRedisTemplate stringRedisTemplate;
    private final SafeRedisUtil safeRedisUtil;
    private final RocketMQTemplate rocketMQTemplate;
    private final NoteLikeDOMapper noteLikeDOMapper;
    private final NoteCollectionDOMapper noteCollectionDOMapper;
    private final CountClient countClient;
    private final TransactionalMqSender transactionalMqSender;
    private final NotePersistenceService notePersistenceService;
    private final NoteInteractionCacheService noteInteractionCacheService;
    private final RedissonClient redissonClient;
    private final UserNoteListService userNoteListService;
    private final OssRpcService ossRpcService;

    private final Cache<Long, ChannelDO> channelLocalCache = Caffeine.newBuilder()
            .initialCapacity(16)
            .maximumSize(64)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    private final Cache<Long, String> topicNameLocalCache = Caffeine.newBuilder()
            .initialCapacity(64)
            .maximumSize(500)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    @Override
    public Response<Boolean> exists(Long noteId) {
        return Response.success(noteDOMapper.selectByPrimaryKey(noteId) != null);
    }

    @Override
    public Response<Boolean> isAccessible(Long noteId) {
        NoteAccessSnapshot accessInfo = loadAccessSnapshot(noteId);
        if (accessInfo == null) {
            return Response.success(Boolean.FALSE);
        }
        Long currentUserId = LoginUserContextHolder.getUserId();
        boolean accessible = Objects.equals(accessInfo.getVisible(), NoteVisibleEnum.PUBLIC.getCode())
                || Objects.equals(currentUserId, accessInfo.getCreatorId());
        return Response.success(accessible);
    }

    @Override
    public Response<List<Long>> findAccessibleNoteIds(List<Long> noteIds) {
        if (CollUtil.isEmpty(noteIds)) {
            return Response.success(Collections.emptyList());
        }
        List<Long> normalizedNoteIds = noteIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(normalizedNoteIds)) {
            return Response.success(Collections.emptyList());
        }
        Long currentUserId = LoginUserContextHolder.getUserId();
        Map<Long, NoteAccessSnapshot> accessSnapshots = loadAccessSnapshots(normalizedNoteIds);
        List<Long> accessibleIds = normalizedNoteIds.stream()
                .filter(noteId -> {
                    NoteAccessSnapshot accessInfo = accessSnapshots.get(noteId);
                    return accessInfo != null && (Objects.equals(accessInfo.getVisible(), NoteVisibleEnum.PUBLIC.getCode())
                            || Objects.equals(accessInfo.getCreatorId(), currentUserId));
                })
                .toList();
        return Response.success(accessibleIds);
    }

    /**
     * 异步写入消费者的最终权限裁决。这里刻意不使用 Redis 快照，保证消费时以 MySQL 当前状态为准。
     */
    @Override
    public Response<List<NoteWriteAccessCheckReqDTO>> findWritableNoteAccesses(
            List<NoteWriteAccessCheckReqDTO> requests) {
        if (CollUtil.isEmpty(requests)) {
            return Response.success(Collections.emptyList());
        }
        List<NoteWriteAccessCheckReqDTO> normalizedRequests = requests.stream()
                .filter(Objects::nonNull)
                .filter(request -> request.getNoteId() != null && request.getUserId() != null)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(normalizedRequests)) {
            return Response.success(Collections.emptyList());
        }
        Map<Long, NoteDO> notes = noteDOMapper.selectAccessInfosByNoteIds(normalizedRequests.stream()
                        .map(NoteWriteAccessCheckReqDTO::getNoteId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(NoteDO::getId, Function.identity(), (left, right) -> left));
        List<NoteWriteAccessCheckReqDTO> writableRequests = normalizedRequests.stream()
                .filter(request -> {
                    NoteDO note = notes.get(request.getNoteId());
                    return note != null && (Objects.equals(note.getVisible(), NoteVisibleEnum.PUBLIC.getCode())
                            || Objects.equals(note.getCreatorId(), request.getUserId()));
                })
                .toList();
        return Response.success(writableRequests);
    }

    /**
     * 笔记详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(90, TimeUnit.SECONDS) // 与 Redis 详情 TTL 同量级，计数自愈 ≤90s
            .build();

    /**
     * 笔记发布
     *
     * @param publishNoteReqVO
     * @return
     */
    @Override
    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO) {
        Integer type = publishNoteReqVO.getType();

        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        Boolean isContentEmpty = true;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                if (CollUtil.isEmpty(imgUriList)) {
                    throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
                }
                if (imgUriList.size() > 8) {
                    throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
                }
                imgUris = StringUtils.join(imgUriList, ",");

                break;
            case VIDEO: // 视频笔记
                videoUri = publishNoteReqVO.getVideoUri();
                if (StringUtils.isBlank(videoUri)) {
                    throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
                }
                break;
            default:
                break;
        }

        String snowflakeId = distributedIdGeneratorRpcService.getSnowflakeId();
        Long noteId = Long.valueOf(snowflakeId);
        String contentUuid = null;

        String content = publishNoteReqVO.getContent();

        if (StringUtils.isNotBlank(content)) {
            isContentEmpty = false;
            contentUuid = UUID.randomUUID().toString();
        }

        Long topicId = publishNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            topicName = topicNameLocalCache.get(topicId, k -> topicDOMapper.selectNameByPrimaryKey(k));
            if (StringUtils.isBlank(topicName)) {
                throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            }
        }

        Long channelId = publishNoteReqVO.getChannelId();
        ChannelDO channel = channelId == null ? null : channelLocalCache.get(channelId, k -> channelDOMapper.selectByPrimaryKey(k));
        if (Objects.isNull(channel) || Boolean.TRUE.equals(channel.getIsDeleted())) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        Long creatorId = LoginUserContextHolder.getUserId();

        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(snowflakeId))
                .isContentEmpty(isContentEmpty)
                .creatorId(creatorId)
                .channelId(channelId)
                .imgUris(imgUris)
                .title(publishNoteReqVO.getTitle())
                .topicId(publishNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .isTop(Boolean.FALSE)
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .revision(1L)
                .build();

        persistPublishedNote(creatorId, noteDO, content);
        return Response.success();
    }

    /**
     * 将笔记元数据快速落库，并异步发送变更事件与失效缓存。
     * @param creatorId
     * @param noteDO
     */
    private void persistPublishedNote(Long creatorId, NoteDO noteDO, String content) {
        List<NoteContentTaskMqDTO> contentTasks = StringUtils.isBlank(content) ? List.of()
                : List.of(buildContentTask(noteDO.getId(), noteDO.getContentUuid(), content, NoteContentTaskTypeEnum.UPSERT));

        NoteChangedEventMqDTO event = NoteChangedEventMqDTO.builder()
                .creatorId(creatorId)
                .noteId(noteDO.getId())
                .changeType(NoteOperateEnum.PUBLISH.getCode())
                .visible(noteDO.getVisible())
                .contentTasks(contentTasks)
                .build();

        // 1. 本地快速落库（单次主键插入，1~2ms 提交并释放 DB 连接）
        notePersistenceService.savePublishedNote(noteDO);

        // 2. 异步投递 MQ 消息至下游（ES 索引同步、Cassandra 正文同步、点赞计数初始化等），完全非阻塞主 HTTP 线程
        String eventPayload = JsonUtils.toJsonString(event);
        threadPoolTaskExecutor.execute(() -> {
            try {
                Message<String> message = MessageBuilder.withPayload(eventPayload)
                        .setHeader(TransactionalMqSender.TX_ID_HEADER, IdUtil.fastSimpleUUID())
                        .build();
                rocketMQTemplate.syncSend(MQConstants.TOPIC_NOTE_CHANGED, message);
            } catch (Exception e) {
                log.warn("异步发送发布笔记变更事件异常, noteId={}", noteDO.getId(), e);
            }
        });

        // 3. 异步失效相关 Redis / 本地缓存
        threadPoolTaskExecutor.execute(() -> invalidateNoteRedisCaches(creatorId, noteDO.getId(), noteDO.getChannelId()));
    }

    /**
     * 提交后失效笔记相关 Redis 缓存（详情快照、作者发布列表、发现页版本）。
     * 尽力而为：失败仅记日志，缓存过期时间兜底。
     * <p>发现页失效改为按频道 bump 版本：只影响所属频道与首页 0。
     *
     * @param channelIds 受影响频道（可为空）；频道 0（首页）总是参与
     */
    private void invalidateNoteRedisCaches(Long creatorId, Long noteId, Long... channelIds) {
        safeRedisUtil.delete(List.of(
                RedisKeyConstants.buildNoteDetailKey(noteId),
                RedisKeyConstants.buildNoteAccessKey(noteId),
                RedisKeyConstants.buildPublishedNoteListKey(creatorId)));
        LOCAL_CACHE.invalidate(noteId);
        CountClient.invalidate(noteId);
        Set<Long> channels = new LinkedHashSet<>();
        channels.add(0L);
        if (channelIds != null) {
            for (Long channelId : channelIds) {
                if (channelId != null) {
                    channels.add(channelId);
                }
            }
        }
        for (Long channelId : channels) {
            bumpDiscoverFeedVersion(channelId);
        }
    }

    // 发现页版本 bump：实时写入最新时间戳推进版本，使旧快照立即失效。
    private void bumpDiscoverFeedVersion(Long channelId) {
        safeRedisUtil.set(
                RedisKeyConstants.buildDiscoverFeedVersionKey(channelId),
                String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 笔记详情
     *
     * @param findNoteDetailReqVO
     * @return
     */
    @Override
    @SneakyThrows
    public Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO) {
        Long noteId = findNoteDetailReqVO.getId();

        Long userId = LoginUserContextHolder.getUserId();

        // 缓存只保存元数据快照（不含正文长文本防 BigKey，不含动态计数保实时）
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        if (StringUtils.isNotBlank(findNoteDetailRspVOStrLocalCache)) {
            if ("null".equals(findNoteDetailRspVOStrLocalCache)) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
            if (isCurrentAndAccessible(noteId, userId, findNoteDetailRspVO)) {
                loadContentAndCounts(findNoteDetailRspVO, userId);
                return Response.success(findNoteDetailRspVO);
            }
            LOCAL_CACHE.invalidate(noteId);
        }

        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = safeRedisUtil.get(noteDetailRedisKey);

        if (StringUtils.isNotBlank(noteDetailJson)) {
            if ("null".equals(noteDetailJson)) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            if (!isCurrentAndAccessible(noteId, userId, findNoteDetailRspVO)) {
                safeRedisUtil.delete(noteDetailRedisKey);
            } else {
                LOCAL_CACHE.put(noteId,
                        Objects.isNull(findNoteDetailRspVO) ? "null" : JsonUtils.toJsonString(findNoteDetailRspVO));
                loadContentAndCounts(findNoteDetailRspVO, userId);
                return Response.success(findNoteDetailRspVO);
            }
        }


        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);

        if (Objects.isNull(noteDO)) {
            // 防止缓存穿透，同步将空数据存入 Redis 缓存 (过期时间不宜设置过长)
            safeRedisUtil.set(noteDetailRedisKey, "null", CacheTtl.minutes(1, 1), TimeUnit.SECONDS);
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        checkNoteVisible(noteDO.getVisible(), userId, noteDO.getCreatorId());

        Integer noteType = noteDO.getType();
        String imgUrisStr = noteDO.getImgUris();
        List<String> imgUris = null;
        if (Objects.equals(noteType, NoteTypeEnum.IMAGE_TEXT.getCode()) && StringUtils.isNotBlank(imgUrisStr)) {
            imgUris = List.of(imgUrisStr.split(","));
        }

        FindNoteDetailRspVO findNoteDetailRspVO = FindNoteDetailRspVO.builder()
                .id(noteDO.getId())
                .revision(noteDO.getRevision())
                .type(noteDO.getType())
                .title(noteDO.getTitle())
                .contentUuid(noteDO.getContentUuid())
                .isContentEmpty(noteDO.getIsContentEmpty())
                .imgUris(imgUris)
                .topicId(noteDO.getTopicId())
                .topicName(noteDO.getTopicName())
                .creatorId(noteDO.getCreatorId())
                .videoUri(noteDO.getVideoUri())
                .updateTime(noteDO.getUpdateTime())
                .visible(noteDO.getVisible())
                .build();

        // 3 个子任务并发并行聚合（作者信息、Cassandra 正文、Redis/RPC 计数）
        CompletableFuture<FindUserByIdRspDTO> authorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return userClient.findById(noteDO.getCreatorId());
            } catch (Exception e) {
                log.warn("并行查询笔记作者信息异常, creatorId={}", noteDO.getCreatorId(), e);
                return null;
            }
        }, threadPoolTaskExecutor);

        CompletableFuture<String> contentFuture = CompletableFuture.supplyAsync(() -> {
            if (Boolean.FALSE.equals(noteDO.getIsContentEmpty()) && StringUtils.isNotBlank(noteDO.getContentUuid())) {
                try {
                    return noteContentRepository.findById(UUID.fromString(noteDO.getContentUuid()))
                            .map(NoteContentDO::getContent)
                            .orElse("");
                } catch (Exception e) {
                    log.warn("并行查询 Cassandra 笔记正文异常, uuid={}", noteDO.getContentUuid(), e);
                }
            }
            return "";
        }, threadPoolTaskExecutor);

        CompletableFuture<Void> countFuture = CompletableFuture.runAsync(() -> {
            fillNoteCounts(findNoteDetailRspVO);
        }, threadPoolTaskExecutor);

        try {
            CompletableFuture.allOf(authorFuture, contentFuture, countFuture)
                    .orTimeout(800, TimeUnit.MILLISECONDS)
                    .join();
        } catch (Exception e) {
            log.warn("并发聚合笔记详情部分子任务超时, noteId={}", noteId, e);
        }

        FindUserByIdRspDTO author = authorFuture.getNow(null);
        findNoteDetailRspVO.setCreatorName(author != null ? author.getNickName() : null);
        findNoteDetailRspVO.setAvatar(author != null ? author.getAvatar() : null);
        findNoteDetailRspVO.setContent(contentFuture.getNow(""));

        // 仅公开笔记写回 Redis 缓存，私密笔记直接读库，节省缓存空间并杜绝越权泄露
        if (Objects.equals(findNoteDetailRspVO.getVisible(), NoteVisibleEnum.PUBLIC.getCode())) {
            threadPoolTaskExecutor.submit(() -> {
                FindNoteDetailRspVO cacheSnapshot = FindNoteDetailRspVO.builder()
                        .id(findNoteDetailRspVO.getId())
                        .type(findNoteDetailRspVO.getType())
                        .title(findNoteDetailRspVO.getTitle())
                        .content("")
                        .imgUris(findNoteDetailRspVO.getImgUris())
                        .topicId(findNoteDetailRspVO.getTopicId())
                        .topicName(findNoteDetailRspVO.getTopicName())
                        .creatorId(findNoteDetailRspVO.getCreatorId())
                        .creatorName(findNoteDetailRspVO.getCreatorName())
                        .avatar(findNoteDetailRspVO.getAvatar())
                        .videoUri(findNoteDetailRspVO.getVideoUri())
                        .updateTime(findNoteDetailRspVO.getUpdateTime())
                        .likeTotal(findNoteDetailRspVO.getLikeTotal())
                        .collectTotal(findNoteDetailRspVO.getCollectTotal())
                        .commentTotal(findNoteDetailRspVO.getCommentTotal())
                        .visible(findNoteDetailRspVO.getVisible())
                        .build();
                String freshNoteDetailJson = JsonUtils.toJsonString(cacheSnapshot);
                safeRedisUtil.set(noteDetailRedisKey, freshNoteDetailJson, CacheTtl.hours(1, 2), TimeUnit.SECONDS);
                LOCAL_CACHE.put(noteId, freshNoteDetailJson);
            });
        }

        if (userId != null) {
            try {
                findNoteDetailRspVO.setIsLiked(noteInteractionCacheService.isLiked(userId, noteId) ? 1 : 0);
                findNoteDetailRspVO.setIsCollected(noteInteractionCacheService.isCollected(userId, noteId) ? 1 : 0);
            } catch (Exception e) {
                log.warn("填充用户笔记互动状态异常, userId={}, noteId={}", userId, noteId, e);
            }
        }

        return Response.success(findNoteDetailRspVO);
    }

    /**
     * 笔记更新
     *
     * @param updateNoteReqVO
     * @return
     */
    @Override
    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO) {
        Long noteId = updateNoteReqVO.getId();
        Integer type = updateNoteReqVO.getType();
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }
        Long currUserId = LoginUserContextHolder.getUserId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        if (!Objects.equals(updateNoteReqVO.getExpectedRevision(), selectNoteDO.getRevision())) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        ChannelDO channelDO = channelDOMapper.selectByPrimaryKey(updateNoteReqVO.getChannelId());
        if (Objects.isNull(channelDO) || Boolean.TRUE.equals(channelDO.getIsDeleted())) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        TopicSnapshot topic = resolveTopic(updateNoteReqVO, selectNoteDO);
        MediaSnapshot media = resolveMedia(updateNoteReqVO, selectNoteDO, noteTypeEnum);
        ContentSnapshot content = resolveContent(updateNoteReqVO, selectNoteDO);
        String oldContentUuid = selectNoteDO.getContentUuid();
        String newContentUuid = content.contentUuid();

        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isContentEmpty(content.isEmpty())
                .channelId(updateNoteReqVO.getChannelId())
                .imgUris(media.imgUris())
                .title(updateNoteReqVO.getTitle())
                .topicId(topic.id())
                .topicName(topic.name())
                .type(type)
                .updateTime(LocalDateTime.now())
                .videoUri(media.videoUri())
                .contentUuid(newContentUuid)
                .revision(updateNoteReqVO.getExpectedRevision())
                .build();

        List<NoteContentTaskMqDTO> contentTasks = new ArrayList<>();
        if (content.createdNewContent()) {
            contentTasks.add(buildContentTask(noteId, newContentUuid, content.value(), NoteContentTaskTypeEnum.UPSERT));
        }
        if (StringUtils.isNotBlank(oldContentUuid) && !Objects.equals(oldContentUuid, newContentUuid)) {
            contentTasks.add(buildContentTask(noteId, oldContentUuid, null, NoteContentTaskTypeEnum.DELETE));
        }

        NoteChangedEventMqDTO event = NoteChangedEventMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .changeType(NoteOperateEnum.UPDATE.getCode())
                .visible(selectNoteDO.getVisible())
                .contentTasks(contentTasks)
                .build();

        // 编辑事实、正文任务与变更事件经由事务消息原子提交；版本冲突在本地事务内抛出并回滚半消息。
        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_NOTE_CHANGED, JsonUtils.toJsonString(event),
                txId -> {
                    notePersistenceService.updateNote(noteDO, txId);
                    return true;
                });

        // 频道可能被修改，新旧频道版本都要 bump。
        invalidateNoteRedisCaches(selectNoteDO.getCreatorId(), noteId,
                selectNoteDO.getChannelId(), updateNoteReqVO.getChannelId());

        List<String> newMediaUrls = new ArrayList<>(StrUtil.split(media.imgUris(), ','));
        if (StringUtils.isNotBlank(media.videoUri())) {
            newMediaUrls.add(media.videoUri());
        }
        List<String> obsoleteMediaUrls = CollUtil.subtractToList(getMediaUrls(selectNoteDO), newMediaUrls);
        if (CollUtil.isNotEmpty(obsoleteMediaUrls)) {
            ossRpcService.deleteFiles(obsoleteMediaUrls, selectNoteDO.getCreatorId());
        }

        return Response.success();
    }

    private TopicSnapshot resolveTopic(UpdateNoteReqVO request, NoteDO current) {
        return switch (request.getTopicOperation()) {
            case KEEP -> new TopicSnapshot(current.getTopicId(), current.getTopicName());
            case CLEAR -> new TopicSnapshot(null, null);
            case SET -> {
                if (request.getTopicId() == null) {
                    throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
                }
                String topicName = topicDOMapper.selectNameByPrimaryKey(request.getTopicId());
                if (StringUtils.isBlank(topicName)) {
                    throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
                }
                yield new TopicSnapshot(request.getTopicId(), topicName);
            }
            case REPLACE -> throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        };
    }

    private MediaSnapshot resolveMedia(UpdateNoteReqVO request, NoteDO current, NoteTypeEnum targetType) {
        if (request.getMediaOperation() == NoteUpdateOperationEnum.KEEP) {
            if (!Objects.equals(current.getType(), targetType.getCode())) {
                throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
            }
            return new MediaSnapshot(current.getImgUris(), current.getVideoUri());
        }
        if (request.getMediaOperation() != NoteUpdateOperationEnum.REPLACE) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        if (targetType == NoteTypeEnum.IMAGE_TEXT) {
            List<String> imgUris = request.getImgUris();
            if (CollUtil.isEmpty(imgUris) || imgUris.size() > 8) {
                throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
            }
            return new MediaSnapshot(StringUtils.join(imgUris, ","), null);
        }
        if (StringUtils.isBlank(request.getVideoUri())) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        return new MediaSnapshot(null, request.getVideoUri());
    }

    private ContentSnapshot resolveContent(UpdateNoteReqVO request, NoteDO current) {
        return switch (request.getContentOperation()) {
            case KEEP -> new ContentSnapshot(current.getContentUuid(), current.getIsContentEmpty(), null, false);
            case CLEAR -> new ContentSnapshot(null, true, null, false);
            case SET -> {
                if (StringUtils.isBlank(request.getContent())) {
                    throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
                }
                String contentUuid = UUID.randomUUID().toString();
                yield new ContentSnapshot(contentUuid, false, request.getContent(), true);
            }
            case REPLACE -> throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        };
    }

    private record TopicSnapshot(Long id, String name) {
    }

    private record MediaSnapshot(String imgUris, String videoUri) {
    }

    private record ContentSnapshot(String contentUuid, Boolean isEmpty, String value, boolean createdNewContent) {
    }

    /**
     * 删除本地笔记缓存
     * @param noteId
     */
    public void deleteNoteLocalCache(Long noteId) {
        LOCAL_CACHE.invalidate(noteId);
    }

    /**
     * 删除笔记
     *
     * @param deleteNoteReqVO
     * @return
     */
    @Override
    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO) {
        Long noteId = deleteNoteReqVO.getId();

        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .revision(selectNoteDO.getRevision())
                .build();

        List<NoteContentTaskMqDTO> contentTasks = StringUtils.isBlank(selectNoteDO.getContentUuid()) ? List.of()
                : List.of(buildContentTask(noteId, selectNoteDO.getContentUuid(), null, NoteContentTaskTypeEnum.DELETE));

        NoteChangedEventMqDTO event = NoteChangedEventMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .changeType(NoteOperateEnum.DELETE.getCode()) // 删除笔记
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .contentTasks(contentTasks)
                .build();

        // 删除事实、正文清理任务与计数扣减经由事务消息原子提交，避免提交前缓存被重新填充。
        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_NOTE_CHANGED, JsonUtils.toJsonString(event),
                txId -> {
                    notePersistenceService.logicalDeleteNote(noteDO, txId);
                    return true;
                });

        invalidateNoteRedisCaches(selectNoteDO.getCreatorId(), noteId, selectNoteDO.getChannelId());

        List<String> mediaUrls = getMediaUrls(selectNoteDO);
        if (CollUtil.isNotEmpty(mediaUrls)) {
            ossRpcService.deleteFiles(mediaUrls, selectNoteDO.getCreatorId());
        }

        return Response.success();
    }

    private List<String> getMediaUrls(NoteDO noteDO) {
        List<String> mediaUrls = new ArrayList<>(StrUtil.split(noteDO.getImgUris(), ','));
        if (StringUtils.isNotBlank(noteDO.getVideoUri())) {
            mediaUrls.add(noteDO.getVideoUri());
        }
        return mediaUrls;
    }

    private NoteContentTaskMqDTO buildContentTask(Long noteId, String contentUuid, String content, NoteContentTaskTypeEnum type) {
        return NoteContentTaskMqDTO.builder()
                .noteId(noteId)
                .contentUuid(contentUuid)
                .content(content)
                .type(type.name())
                .build();
    }

    /**
     * 笔记仅对自己可见
     *
     * @param updateNoteVisibleOnlyMeReqVO
     * @return
     */
    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        return updateVisibility(UpdateNoteVisibilityReqVO.builder()
                .id(updateNoteVisibleOnlyMeReqVO.getId())
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .build());
    }

    @Override
    public Response<?> updateVisibility(UpdateNoteVisibilityReqVO request) {
        Long noteId = request.getId();
        Integer visible = request.getVisible();
        if (!Objects.equals(visible, NoteVisibleEnum.PUBLIC.getCode())
                && !Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(visible)
                .updateTime(LocalDateTime.now())
                .build();

        NoteChangedEventMqDTO event = NoteChangedEventMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .changeType(NoteOperateEnum.UPDATE.getCode())
                .visible(visible)
                .contentTasks(List.of())
                .build();

        // 可见性变更经由事务消息分发变更事件，确保下游（如 Elasticsearch）及时同步下架/上架状态
        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_NOTE_CHANGED, JsonUtils.toJsonString(event),
                txId -> {
                    notePersistenceService.updateNoteVisibility(noteDO, txId);
                    return true;
                });

        invalidateNoteRedisCaches(currUserId, noteId, selectNoteDO.getChannelId());
        LOCAL_CACHE.invalidate(noteId);

        return Response.success();
    }

    /**
     * 笔记置顶 / 取消置顶
     *
     * @param topNoteReqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> topNote(TopNoteReqVO topNoteReqVO) {
        Long noteId = topNoteReqVO.getId();
        Boolean isTop = topNoteReqVO.getIsTop();

        Long currUserId = LoginUserContextHolder.getUserId();

        // 置顶改变频道排序，需按频道 ID bump 版本。
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(isTop)
                .updateTime(LocalDateTime.now())
                .creatorId(currUserId) // 只有笔记所有者，才能置顶/取消置顶笔记
                .build();

        int count = noteDOMapper.updateIsTop(noteDO);

        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        registerPostCommitCacheInvalidation(currUserId, noteId,
                selectNoteDO == null ? null : selectNoteDO.getChannelId());

        return Response.success();
    }

    /**
     * 纯 DB 更新事务（可见性、置顶）提交后再失效 Redis 缓存，尽力而为。
     */
    private void registerPostCommitCacheInvalidation(Long creatorId, Long noteId, Long channelId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidateNoteRedisCaches(creatorId, noteId, channelId);
            }
        });
    }

    /**
     * 点赞笔记
     *
     * @param likeNoteReqVO
     * @return
     */
    @Override
    public Response<?> likeNote(LikeNoteReqVO likeNoteReqVO) {
        Long noteId = likeNoteReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        if (!noteInteractionCacheService.addLike(userId, noteId)) {
            throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
        }

        LocalDateTime now = LocalDateTime.now();
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.LIKE.getCode()) // 点赞笔记
                .createTime(now)
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;

        // 分区键：同一用户操作路由到同一队列，保证消费端顺序
        String hashKey = String.valueOf(userId);

        try {
            // 同步顺序发送（hashKey=userId 保持同用户操作有序），失败回滚缓存后抛出，避免假成功
            RocketMqHelper.syncSendOrderly(rocketMQTemplate, destination, message, hashKey, "笔记点赞");
            CountClient.invalidate(noteId);
        } catch (Exception e) {
            noteInteractionCacheService.evictLikeCaches(userId);
            throw e;
        }

        return Response.success();
    }

    /**
     * 取消点赞笔记
     *
     * @param unlikeNoteReqVO
     * @return
     */
    @Override
    public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        Long noteId = unlikeNoteReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        if (!noteInteractionCacheService.removeLike(userId, noteId)) {
            // 缓存未命中（说明可能处于 1000 条之后的较早历史数据）：回源 DB 校验是否点赞过
            if (noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId) == 0) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
            }
        }

        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.UNLIKE.getCode()) // 取消点赞笔记
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;

        // 分区键：同一用户操作路由到同一队列，保证消费端顺序
        String hashKey = String.valueOf(userId);

        try {
            RocketMqHelper.syncSendOrderly(rocketMQTemplate, destination, message, hashKey, "笔记取消点赞");
            CountClient.invalidate(noteId);
        } catch (Exception e) {
            noteInteractionCacheService.evictLikeCaches(userId);
            throw e;
        }

        return Response.success();
    }

    /**
     * 收藏笔记
     *
     * @param collectNoteReqVO
     * @return
     */
    @Override
    public Response<?> collectNote(CollectNoteReqVO collectNoteReqVO) {
        Long noteId = collectNoteReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        if (!noteInteractionCacheService.addCollect(userId, noteId)) {
            throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
        }

        LocalDateTime now = LocalDateTime.now();
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.COLLECT.getCode()) // 收藏笔记
                .createTime(now)
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(collectUnCollectNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_COLLECT;

        // 分区键：同一用户操作路由到同一队列，保证消费端顺序
        String hashKey = String.valueOf(userId);

        try {
            RocketMqHelper.syncSendOrderly(rocketMQTemplate, destination, message, hashKey, "笔记收藏");
            CountClient.invalidate(noteId);
        } catch (Exception e) {
            noteInteractionCacheService.evictCollectCaches(userId);
            throw e;
        }

        return Response.success();
    }

    /**
     * 取消收藏笔记
     *
     * @param unCollectNoteReqVO
     * @return
     */
    @Override
    public Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO) {
        Long noteId = unCollectNoteReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        if (!noteInteractionCacheService.removeCollect(userId, noteId)) {
            // 缓存未命中（说明可能处于 1000 条之后的较早历史数据）：回源 DB 校验是否收藏过
            if (noteCollectionDOMapper.selectCountByUserIdAndNoteId(userId, noteId) == 0) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
            }
        }

        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.UN_COLLECT.getCode()) // 取消收藏笔记
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unCollectNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_UN_COLLECT;

        // 分区键：同一用户操作路由到同一队列，保证消费端顺序
        String hashKey = String.valueOf(userId);

        try {
            RocketMqHelper.syncSendOrderly(rocketMQTemplate, destination, message, hashKey, "笔记取消收藏");
            CountClient.invalidate(noteId);
        } catch (Exception e) {
            noteInteractionCacheService.evictCollectCaches(userId);
            throw e;
        }

        return Response.success();
    }

    /**
     * 获取是否点赞、收藏数据
     *
     * @param findNoteIsLikedAndCollectedReqVO
     * @return
     */
    @Override
    public Response<FindNoteIsLikedAndCollectedRspVO> isLikedAndCollectedData(FindNoteIsLikedAndCollectedReqVO findNoteIsLikedAndCollectedReqVO) {
        Long noteId = findNoteIsLikedAndCollectedReqVO.getNoteId();
        checkNoteIsExistAndGetCreatorId(noteId);

        Long currUserId = LoginUserContextHolder.getUserId();

        boolean isLiked = false;
        boolean isCollected = false;

        if (Objects.nonNull((currUserId))) {
            isLiked = checkNoteIsLiked(noteId, currUserId);

            isCollected = checkNoteIsCollected(noteId, currUserId);
        }

        return Response.success(FindNoteIsLikedAndCollectedRspVO.builder()
                        .noteId(noteId)
                        .isLiked(isLiked)
                        .isCollected(isCollected)
                        .build());
    }

    /**
     * 用户主页 - 查询已发布的笔记列表
     *
     * @param findPublishedNoteListReqVO
     * @return
     */
    @Override
    public Response<FindPublishedNoteListRspVO> findPublishedNoteList(FindPublishedNoteListReqVO findPublishedNoteListReqVO) {
        Long userId = findPublishedNoteListReqVO.getUserId();
        Long cursor = findPublishedNoteListReqVO.getCursor();
        boolean includePrivate = Objects.equals(LoginUserContextHolder.getUserId(), userId);

        FindPublishedNoteListRspVO findPublishedNoteListRspVO = null;

        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(userId);
        if (!includePrivate && Objects.isNull(cursor)) {
            String publishedNoteListJson = stringRedisTemplate.opsForValue().get(publishedNoteListRedisKey);

            if (StringUtils.isNotBlank(publishedNoteListJson)) {
                try {
                    log.debug("已发布笔记列表命中了 Redis 缓存...");
                    List<NoteItemRspVO> noteItemRspVOS = JsonUtils.parseList(publishedNoteListJson, NoteItemRspVO.class);
                    List<NoteItemRspVO> sortedList = noteItemRspVOS.stream().sorted(Comparator.comparing(NoteItemRspVO::getNoteId).reversed()).toList();

                    // 实时回填当前用户点赞状态（计数复用快照内嵌基准值，零 Feign 零 Hash 往返）
                    batchGetAndSetNoteIsLiked(sortedList);

                    // 作者本人查看时实时刷新点赞计数，避免命中缓存读到 30~60 分钟前的旧值
                    getAndSetLatestLikeTotalIfAuthor(userId, sortedList);

                    Optional<Long> earliestNoteId = sortedList.stream().map(NoteItemRspVO::getNoteId).min(Long::compareTo);

                    findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                            .notes(sortedList)
                            .nextCursor(earliestNoteId.orElse(null))
                            .build();
                    return Response.success(findPublishedNoteListRspVO);
                } catch (Exception e) {
                    log.error("读取已发布笔记缓存失败", e);
                }
            }
        }

        List<NoteDO> noteDOS = noteDOMapper.selectPublishedNoteListByUserIdAndCursor(userId, cursor, includePrivate);

        if (CollUtil.isNotEmpty(noteDOS)) {
            List<NoteItemRspVO> noteVOS = noteDOS.stream()
                    .map(noteDO -> {
                        String cover = StringUtils.isNotBlank(noteDO.getImgUris()) ?
                                StringUtils.split(noteDO.getImgUris(), ",")[0] : null;

                        NoteItemRspVO noteItemRspVO = NoteItemRspVO.builder()
                                .noteId(noteDO.getId())
                                .type(noteDO.getType())
                                .creatorId(noteDO.getCreatorId())
                                .cover(cover)
                                .videoUri(noteDO.getVideoUri())
                                .title(noteDO.getTitle())
                                .isLiked(false) // 默认为未点赞状态
                                .build();
                        return noteItemRspVO;
                    }).toList();

            CompletableFuture<FindUserByIdRspDTO> userFuture = CompletableFuture
                    .supplyAsync(() -> userClient.findById(userId), threadPoolTaskExecutor);

            CompletableFuture<List<FindNoteCountsByIdRspDTO>> noteCountFuture = CompletableFuture
                    .supplyAsync(() -> {
                        List<Long> noteIds = noteDOS.stream().map(NoteDO::getId).toList();
                        return countClient.findByNoteIds(noteIds);
                    }, threadPoolTaskExecutor);

            try {
                FindUserByIdRspDTO findUserByIdRspDTO = userFuture.get();
                List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = noteCountFuture.get();

                if (Objects.nonNull(findUserByIdRspDTO)) {
                    noteVOS.forEach(noteItemRspVO -> {
                        noteItemRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                        noteItemRspVO.setNickname(findUserByIdRspDTO.getNickName());
                    });
                }

                setVOListLikeTotal(noteVOS, findNoteCountsByIdRspDTOS);

                batchGetAndSetNoteIsLiked(noteVOS);
            } catch (Exception e) {
                log.error("## 并发调用错误: ", e);
            }

            Optional<Long> earliestNoteId = noteDOS.stream().map(NoteDO::getId).min(Long::compareTo);

            findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                    .notes(noteVOS)
                    .nextCursor(earliestNoteId.orElse(null))
                    .build();

            if (!includePrivate && Objects.isNull(cursor)) {
                syncFirstPagePublishedNoteList2Redis(noteVOS, publishedNoteListRedisKey);
            }
        } else {
            findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                    .notes(Collections.emptyList())
                    .nextCursor(null)
                    .build();
        }

        return Response.success(findPublishedNoteListRspVO);
    }

    @Override
    public Response<FindNoteActionListRspVO> findCollectedNoteList(FindNoteActionListReqVO request) {
        checkCurrentUserOwnsList(request.getUserId());
        return userNoteListService.findCollectedNotes(request);
    }

    @Override
    public Response<FindNoteActionListRspVO> findLikedNoteList(FindNoteActionListReqVO request) {
        checkCurrentUserOwnsList(request.getUserId());
        return userNoteListService.findLikedNotes(request);
    }

    /**
     * 批量获取笔记的点赞状态
     */
    private void batchGetAndSetNoteIsLiked(List<NoteItemRspVO> noteItemRspVOS) {
        Long loginUserId = LoginUserContextHolder.getUserId();
        if (Objects.nonNull(loginUserId)) {
            List<Long> noteIds = noteItemRspVOS.stream().map(NoteItemRspVO::getNoteId).toList();
            Set<Long> likedNoteIds = noteInteractionCacheService.findLikedNoteIds(loginUserId, noteIds);
            noteItemRspVOS.forEach(note -> note.setIsLiked(likedNoteIds.contains(note.getNoteId())));
        }
    }

    /**
     * 作者本人查看已发布笔记时，实时从计数服务刷新点赞数，避免命中旧快照缓存
     */
    private void getAndSetLatestLikeTotalIfAuthor(Long userId, List<NoteItemRspVO> sortedList) {
        Long loginUserId = LoginUserContextHolder.getUserId();
        if (Objects.nonNull(loginUserId) && Objects.equals(loginUserId, userId)) {
            List<Long> noteIds = sortedList.stream().map(NoteItemRspVO::getNoteId).toList();
            List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = countClient.findByNoteIds(noteIds);

            setVOListLikeTotal(sortedList, findNoteCountsByIdRspDTOS);
        }
    }

    /**
     * 设置 VO 集合中每篇笔记的点赞量
     */
    private static void setVOListLikeTotal(List<NoteItemRspVO> noteItemRspVOS, List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS) {
        if (CollUtil.isNotEmpty(findNoteCountsByIdRspDTOS)) {
            Map<Long, FindNoteCountsByIdRspDTO> noteIdAndDTOMap = findNoteCountsByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId, dto -> dto, (a, b) -> a));

            noteItemRspVOS.forEach(noteItemRspVO -> {
                Long currNoteId = noteItemRspVO.getNoteId();
                FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO = noteIdAndDTOMap.get(currNoteId);
                noteItemRspVO.setLikeTotal((Objects.nonNull(findNoteCountsByIdRspDTO) && Objects.nonNull(findNoteCountsByIdRspDTO.getLikeTotal())) ?
                        NumberUtils.formatNumberString(findNoteCountsByIdRspDTO.getLikeTotal()) : "0");
            });
        }
    }

    /**
     * 同步第一页已发布笔记到 Redis
     * @param noteVOS
     * @param publishedNoteListRedisKey
     */
    private void syncFirstPagePublishedNoteList2Redis(List<NoteItemRspVO> noteVOS, String publishedNoteListRedisKey) {
        if (CollUtil.isEmpty(noteVOS)) return;
        threadPoolTaskExecutor.submit(() -> {
            safeRedisUtil.setObject(publishedNoteListRedisKey, noteVOS, CacheTtl.minutes(30, 30), TimeUnit.SECONDS);
        });
    }

    /**
     * 校验当前用户是否点赞笔记
     * @param noteId
     * @param currUserId
     * @return
     */
    private boolean checkNoteIsLiked(Long noteId, Long currUserId) {
        return noteInteractionCacheService.isLiked(currUserId, noteId);
    }

    /**
     * 校验当前用户是否收藏笔记
     * @param noteId
     * @param currUserId
     * @return
     */
    private boolean checkNoteIsCollected(Long noteId, Long currUserId) {
        return noteInteractionCacheService.isCollected(currUserId, noteId);
    }


    /**
     * 校验笔记是否存在，若存在，则获取笔记的发布者 ID
     * @param noteId
     */
    private Long checkNoteIsExistAndGetCreatorId(Long noteId) {
        // 仅供互动状态读取使用；详情、评论等读取权限允许短暂最终一致。
        NoteAccessSnapshot accessInfo = loadAccessSnapshot(noteId);
        if (accessInfo == null) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        checkNoteVisible(accessInfo.getVisible(), LoginUserContextHolder.getUserId(), accessInfo.getCreatorId());
        return accessInfo.getCreatorId();
    }

    private void checkCurrentUserOwnsList(Long requestedUserId) {
        Long currentUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currentUserId, requestedUserId)) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
    }

    private void loadContentAndCounts(FindNoteDetailRspVO findNoteDetailRspVO, Long userId) {
        if (findNoteDetailRspVO == null) {
            return;
        }
        Long noteId = findNoteDetailRspVO.getId();
        // 并行加载正文、计数与用户互动状态
        CompletableFuture<String> contentFuture = CompletableFuture.supplyAsync(() -> {
            if (Boolean.FALSE.equals(findNoteDetailRspVO.getIsContentEmpty()) && StringUtils.isNotBlank(findNoteDetailRspVO.getContentUuid())) {
                try {
                    return noteContentRepository.findById(UUID.fromString(findNoteDetailRspVO.getContentUuid()))
                            .map(NoteContentDO::getContent)
                            .orElse("");
                } catch (Exception e) {
                    log.warn("Cassandra 查询笔记正文异常, uuid={}", findNoteDetailRspVO.getContentUuid(), e);
                }
            }
            return "";
        }, threadPoolTaskExecutor);

        CompletableFuture<Void> countFuture = CompletableFuture.runAsync(() -> {
            fillNoteCounts(findNoteDetailRspVO);
        }, threadPoolTaskExecutor);

        CompletableFuture<Void> interactionFuture = CompletableFuture.runAsync(() -> {
            if (userId != null && noteId != null) {
                try {
                    findNoteDetailRspVO.setIsLiked(noteInteractionCacheService.isLiked(userId, noteId) ? 1 : 0);
                    findNoteDetailRspVO.setIsCollected(noteInteractionCacheService.isCollected(userId, noteId) ? 1 : 0);
                } catch (Exception e) {
                    log.warn("填充用户笔记互动状态异常, userId={}, noteId={}", userId, noteId, e);
                }
            }
        }, threadPoolTaskExecutor);

        try {
            CompletableFuture.allOf(contentFuture, countFuture, interactionFuture).orTimeout(500, TimeUnit.MILLISECONDS).join();
            findNoteDetailRspVO.setContent(contentFuture.join());
        } catch (Exception e) {
            log.warn("并发加载笔记正文、计数与互动状态部分超时, noteId={}", findNoteDetailRspVO.getId(), e);
            findNoteDetailRspVO.setContent(contentFuture.getNow(""));
        }
    }

    private void fillNoteCounts(FindNoteDetailRspVO noteDetail) {
        if (noteDetail == null || noteDetail.getId() == null) {
            return;
        }
        Long noteId = noteDetail.getId();
        String countKey = CountKeyConstants.buildCountNoteKey(noteId);
        List<Object> hashValues = safeRedisUtil.hMultiGet(countKey,
                List.of(CountKeyConstants.FIELD_LIKE_TOTAL,
                        CountKeyConstants.FIELD_COLLECT_TOTAL,
                        CountKeyConstants.FIELD_COMMENT_TOTAL));
        if (CollUtil.isNotEmpty(hashValues) && hashValues.size() >= 3) {
            boolean hasValidCount = false;
            if (hashValues.get(0) != null) {
                noteDetail.setLikeTotal(Long.parseLong(String.valueOf(hashValues.get(0))));
                hasValidCount = true;
            }
            if (hashValues.get(1) != null) {
                noteDetail.setCollectTotal(Long.parseLong(String.valueOf(hashValues.get(1))));
                hasValidCount = true;
            }
            if (hashValues.get(2) != null) {
                noteDetail.setCommentTotal(Long.parseLong(String.valueOf(hashValues.get(2))));
                hasValidCount = true;
            }
            if (hasValidCount) {
                return;
            }
        }

        try {
            List<FindNoteCountsByIdRspDTO> counts = countClient.findByNoteIds(List.of(noteId));
            FindNoteCountsByIdRspDTO count = CollUtil.isEmpty(counts) ? null : counts.get(0);
            if (count != null) {
                noteDetail.setLikeTotal(count.getLikeTotal() != null ? count.getLikeTotal() : 0L);
                noteDetail.setCollectTotal(count.getCollectTotal() != null ? count.getCollectTotal() : 0L);
                noteDetail.setCommentTotal(count.getCommentTotal() != null ? count.getCommentTotal() : 0L);
            } else {
                noteDetail.setLikeTotal(0L);
                noteDetail.setCollectTotal(0L);
                noteDetail.setCommentTotal(0L);
            }
        } catch (Exception e) {
            log.warn("查询笔记计数失败，noteId={}", noteId, e);
            noteDetail.setLikeTotal(0L);
            noteDetail.setCollectTotal(0L);
            noteDetail.setCommentTotal(0L);
        }
    }

    /**
     * 校验笔记的可见性
     * @param visible 是否可见
     * @param currUserId 当前用户 ID
     * @param creatorId 笔记创建者
     */
    private void checkNoteVisible(Integer visible, Long currUserId, Long creatorId) {
        if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(currUserId, creatorId)) { // 仅自己可见, 并且访问用户不是笔记创建者
            throw new BizException(ResponseCodeEnum.NOTE_PRIVATE);
        }
    }

    private NoteAccessSnapshot requireAccessibleNote(Long noteId, Long userId) {
        NoteAccessSnapshot accessInfo = loadAccessSnapshot(noteId);
        if (accessInfo == null) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        checkNoteVisible(accessInfo.getVisible(), userId, accessInfo.getCreatorId());
        return accessInfo;
    }

    private boolean isCurrentAndAccessible(Long noteId, Long userId, FindNoteDetailRspVO snapshot) {
        if (snapshot == null || snapshot.getRevision() == null) {
            return false;
        }
        NoteAccessSnapshot accessInfo = requireAccessibleNote(noteId, userId);
        return Objects.equals(snapshot.getRevision(), accessInfo.getRevision());
    }

    /**
     * 访问控制允许短暂最终一致：笔记变更会由现有 MQ 立即删除该快照，未命中时回源 MySQL。
     */
    private NoteAccessSnapshot loadAccessSnapshot(Long noteId) {
        String key = RedisKeyConstants.buildNoteAccessKey(noteId);
        // 负缓存（"null" 哨兵）直接短路，避免已删除/不存在的笔记反复回源重建
        String cachedValue = getAccessSnapshotCacheValue(key);
        if ("null".equals(cachedValue)) {
            return null;
        }
        NoteAccessSnapshot cachedSnapshot = readAccessSnapshot(key);
        if (cachedSnapshot != null) {
            return cachedSnapshot;
        }
        String lockKey = RedisKeyConstants.buildNoteAccessRebuildLockKey(noteId);
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("获取笔记访问快照重建锁异常, lockKey={}", lockKey, e);
        }

        if (acquired) {
            try {
                cachedSnapshot = readAccessSnapshot(key);
                if (cachedSnapshot != null) {
                    return cachedSnapshot;
                }
                return loadAccessSnapshotFromMySql(noteId, key, true);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        cachedSnapshot = readAccessSnapshot(key);
        return cachedSnapshot != null ? cachedSnapshot : loadAccessSnapshotFromMySql(noteId, key, false);
    }

    /** 读取访问快照缓存；空白/"null"/解析失败统一走重建（解析失败先删脏值）。 */
    private NoteAccessSnapshot readAccessSnapshot(String key) {
        return safeRedisUtil.getObject(key, NoteAccessSnapshot.class);
    }

    private NoteAccessSnapshot loadAccessSnapshotFromMySql(Long noteId, String key, boolean cacheResult) {
        NoteDO note = noteDOMapper.selectAccessInfoByNoteId(noteId);
        if (note == null) {
            if (cacheResult) {
                cacheAccessSnapshot(key, "null");
            }
            return null;
        }
        NoteAccessSnapshot snapshot = toAccessSnapshot(note);
        if (cacheResult) {
            cacheAccessSnapshot(key, JsonUtils.toJsonString(snapshot));
        }
        return snapshot;
    }

    /**
     * 批量获取笔记访问控制快照。Redis 冷缓存时只进行一次 IN 查询，避免批量评论校验退化为 N 次主键查询。
     */
    private Map<Long, NoteAccessSnapshot> loadAccessSnapshots(List<Long> noteIds) {
        List<String> keys = noteIds.stream()
                .map(RedisKeyConstants::buildNoteAccessKey)
                .toList();
        List<String> cachedValues = safeRedisUtil.multiGet(keys);
        if (CollUtil.isEmpty(cachedValues)) {
            return loadAccessSnapshotsFromMySql(noteIds);
        }
        Map<Long, NoteAccessSnapshot> snapshots = new HashMap<>(noteIds.size());
        List<Long> missedNoteIds = new ArrayList<>();

        for (int i = 0; i < noteIds.size(); i++) {
            String cached = cachedValues.size() <= i ? null : cachedValues.get(i);
            if (StringUtils.isBlank(cached)) {
                missedNoteIds.add(noteIds.get(i));
                continue;
            }
            if ("null".equals(cached)) {
                continue;
            }
            try {
                snapshots.put(noteIds.get(i), JsonUtils.parseObject(cached, NoteAccessSnapshot.class));
            } catch (Exception e) {
                deleteAccessSnapshotCache(keys.get(i));
                missedNoteIds.add(noteIds.get(i));
            }
        }

        if (CollUtil.isEmpty(missedNoteIds)) {
            return snapshots;
        }

        snapshots.putAll(loadAccessSnapshotsFromMySql(missedNoteIds));
        return snapshots;
    }

    private Map<Long, NoteAccessSnapshot> loadAccessSnapshotsFromMySql(List<Long> noteIds) {
        List<NoteDO> notes = noteDOMapper.selectAccessInfosByNoteIds(noteIds);
        Map<Long, NoteAccessSnapshot> databaseSnapshots = notes.stream()
                .collect(Collectors.toMap(NoteDO::getId, this::toAccessSnapshot, (left, right) -> left));
        Map<Long, NoteAccessSnapshot> snapshots = new HashMap<>(databaseSnapshots.size());
        for (Long noteId : noteIds) {
            String key = RedisKeyConstants.buildNoteAccessKey(noteId);
            NoteAccessSnapshot snapshot = databaseSnapshots.get(noteId);
            if (snapshot == null) {
                // status 非正常或不存在的笔记也做短期缓存，避免缓存穿透。
                cacheAccessSnapshot(key, "null");
                continue;
            }
            snapshots.put(noteId, snapshot);
            cacheAccessSnapshot(key, JsonUtils.toJsonString(snapshot));
        }
        return snapshots;
    }

    private String getAccessSnapshotCacheValue(String key) {
        return safeRedisUtil.get(key);
    }

    private void cacheAccessSnapshot(String key, String value) {
        safeRedisUtil.set(key, value, 30, TimeUnit.SECONDS);
    }

    private void deleteAccessSnapshotCache(String key) {
        safeRedisUtil.delete(key);
    }

    private NoteAccessSnapshot toAccessSnapshot(NoteDO note) {
        NoteAccessSnapshot snapshot = new NoteAccessSnapshot();
        snapshot.setCreatorId(note.getCreatorId());
        snapshot.setVisible(note.getVisible());
        snapshot.setRevision(note.getRevision());
        return snapshot;
    }

}
