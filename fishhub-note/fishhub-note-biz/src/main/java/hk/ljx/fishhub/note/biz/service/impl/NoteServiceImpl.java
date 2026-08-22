package hk.ljx.fishhub.note.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Lists;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.common.util.NumberUtils;
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
import hk.ljx.fishhub.kv.client.KeyValueClient;
import hk.ljx.fishhub.note.biz.rpc.DistributedIdGeneratorRpcService;
import hk.ljx.fishhub.note.biz.rpc.OssRpcService;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.support.RocketMqHelper;
import hk.ljx.fishhub.note.biz.service.NoteService;
import hk.ljx.fishhub.note.biz.service.NotePersistenceService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.note.biz.service.UserNoteListService;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import hk.ljx.framework.common.util.RedisScriptHelper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
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


@Service
@Slf4j
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private static final int ACCESS_SNAPSHOT_REBUILD_RETRY_TIMES = 3;
    private static final long ACCESS_SNAPSHOT_REBUILD_RETRY_INTERVAL_MILLIS = 20L;
    private static final long ACCESS_SNAPSHOT_REBUILD_LOCK_SECONDS = 2L;

    private final NoteDOMapper noteDOMapper;
    private final TopicDOMapper topicDOMapper;
    private final ChannelDOMapper channelDOMapper;
    private final DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    private final KeyValueClient keyValueClient;
    private final UserClient userClient;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final StringRedisTemplate stringRedisTemplate;
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
                .collect(Collectors.toMap(NoteDO::getId, Function.identity()));
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
        // 笔记类型
        Integer type = publishNoteReqVO.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        // 笔记内容是否为空，默认值为 true，即空
        Boolean isContentEmpty = true;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                // 校验图片是否为空
                if (CollUtil.isEmpty(imgUriList)) {
                    throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
                }
                // 校验图片数量
                if (imgUriList.size() > 8) {
                    throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
                }
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");

                break;
            case VIDEO: // 视频笔记
                videoUri = publishNoteReqVO.getVideoUri();
                // 校验视频链接是否为空
                if (StringUtils.isBlank(videoUri)) {
                    throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
                }
                break;
            default:
                break;
        }

        // RPC: 调用分布式 ID 生成服务，生成笔记 ID
        String snowflakeId = distributedIdGeneratorRpcService.getSnowflakeId();
        Long noteId = Long.valueOf(snowflakeId);
        // 笔记内容 UUID
        String contentUuid = null;

        // 笔记内容
        String content = publishNoteReqVO.getContent();

        // 若用户填写了笔记内容
        if (StringUtils.isNotBlank(content)) {
            // 内容是否为空，置为 false，即不为空
            isContentEmpty = false;
            // 生成笔记内容 UUID
            contentUuid = UUID.randomUUID().toString();
        }

        // 话题
        Long topicId = publishNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            // 获取话题名称
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
            if (StringUtils.isBlank(topicName)) {
                throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            }
        }

        Long channelId = publishNoteReqVO.getChannelId();
        ChannelDO channel = channelDOMapper.selectByPrimaryKey(channelId);
        if (Objects.isNull(channel) || Boolean.TRUE.equals(channel.getIsDeleted())) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        // 发布者用户 ID
        Long creatorId = LoginUserContextHolder.getUserId();

        // 构建笔记 DO 对象
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
     * 将笔记元数据和发布事件一起持久化。
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
                .contentTasks(contentTasks)
                .build();

        // 笔记元数据、正文任务与发布事件经由事务消息原子提交。
        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_NOTE_CHANGED, JsonUtils.toJsonString(event),
                txId -> {
                    notePersistenceService.savePublishedNote(noteDO, txId);
                    return true;
                });

        // Redis 为共享存储，提交后于本进程内直接失效，无需跨节点事件；
        // 各节点本地缓存由读路径的最小事实校验兜底。
        invalidateNoteRedisCaches(creatorId, noteDO.getId(), noteDO.getChannelId());
    }

    /**
     * 提交后失效笔记相关 Redis 缓存（详情快照、作者发布列表、发现页版本）。
     * 尽力而为：失败仅记日志，缓存过期时间兜底。
     * <p>发现页失效改为按频道 bump 版本：只影响所属频道与首页 0。
     *
     * @param channelIds 受影响频道（可为空）；频道 0（首页）总是参与
     */
    private void invalidateNoteRedisCaches(Long creatorId, Long noteId, Long... channelIds) {
        try {
            stringRedisTemplate.delete(List.of(
                    RedisKeyConstants.buildNoteDetailKey(noteId),
                    RedisKeyConstants.buildNoteAccessKey(noteId),
                    RedisKeyConstants.buildPublishedNoteListKey(creatorId)));
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
        } catch (Exception e) {
            log.warn("笔记缓存失效失败，等待缓存过期兜底, noteId={}", noteId, e);
        }
    }

    // 发现页版本 bump：实时写入最新时间戳推进版本，使旧快照立即失效。
    private void bumpDiscoverFeedVersion(Long channelId) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.buildDiscoverFeedVersionKey(channelId),
                    String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("Redis 不可用，发现页版本 bump 失败，等待缓存过期兜底, channelId={}", channelId, e);
        }
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
        // 查询的笔记 ID
        Long noteId = findNoteDetailReqVO.getId();

        // 当前登录用户
        Long userId = LoginUserContextHolder.getUserId();

        // 缓存只保存详情快照；访问权限和版本以 MySQL 中的最小事实字段为准。
        // 因而可见性变更/删除无需等待跨节点缓存失效消息，即不会继续暴露旧公开快照。
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        if (StringUtils.isNotBlank(findNoteDetailRspVOStrLocalCache)) {
            if ("null".equals(findNoteDetailRspVOStrLocalCache)) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
            if (isCurrentAndAccessible(noteId, userId, findNoteDetailRspVO)) {
                // 实时回填最新计数
                fillNoteCounts(findNoteDetailRspVO);
                return Response.success(findNoteDetailRspVO);
            }
            LOCAL_CACHE.invalidate(noteId);
        }

        // 从 Redis 缓存中获取
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = stringRedisTemplate.opsForValue().get(noteDetailRedisKey);

        // 若缓存中有该笔记的数据，则直接返回
        if (StringUtils.isNotBlank(noteDetailJson)) {
            if ("null".equals(noteDetailJson)) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            if (!isCurrentAndAccessible(noteId, userId, findNoteDetailRspVO)) {
                stringRedisTemplate.delete(noteDetailRedisKey);
            } else {
                // 写入本地缓存
                LOCAL_CACHE.put(noteId,
                        Objects.isNull(findNoteDetailRspVO) ? "null" : JsonUtils.toJsonString(findNoteDetailRspVO));
                // 实时回填最新计数
                fillNoteCounts(findNoteDetailRspVO);
                return Response.success(findNoteDetailRspVO);
            }
        }


        // 若 Redis 缓存中获取不到，则走数据库查询
        // 查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 若该笔记不存在，则抛出业务异常
        if (Objects.isNull(noteDO)) {
            try {
                // 防止缓存穿透，同步将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                long expireSeconds = CacheTtl.minutes(1, 1);
                stringRedisTemplate.opsForValue().set(noteDetailRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis 不可用，写入防穿透空值缓存失败, noteId={}", noteId, e);
            }
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 可见性校验
        checkNoteVisible(noteDO.getVisible(), userId, noteDO.getCreatorId());

        // 并发查询优化
        // RPC: 调用用户服务
        CompletableFuture<FindUserByIdRspDTO> userResultFuture = CompletableFuture
                .supplyAsync(() -> userClient.findById(noteDO.getCreatorId()), threadPoolTaskExecutor);

        // RPC: 调用 K-V 存储服务获取内容
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE)) {
            contentResultFuture = CompletableFuture
                    .supplyAsync(() -> keyValueClient.findNoteContent(noteDO.getContentUuid()), threadPoolTaskExecutor);
        }

        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        CompletableFuture<FindNoteDetailRspVO> resultFuture = CompletableFuture
                .allOf(userResultFuture, contentResultFuture)
                .thenApply(s -> {
                    // 获取 Future 返回的结果
                    FindUserByIdRspDTO findUserByIdRspDTO = userResultFuture.join();
                    String content = finalContentResultFuture.join();

                    // 笔记类型
                    Integer noteType = noteDO.getType();
                    // 图文笔记图片链接(字符串)
                    String imgUrisStr = noteDO.getImgUris();
                    // 图文笔记图片链接(集合)
                    List<String> imgUris = null;
                    // 如果查询的是图文笔记，需要将图片链接的逗号分隔开，转换成集合
                    if (Objects.equals(noteType, NoteTypeEnum.IMAGE_TEXT.getCode())
                            && StringUtils.isNotBlank(imgUrisStr)) {
                        imgUris = List.of(imgUrisStr.split(","));
                    }

                    return FindNoteDetailRspVO.builder()
                            .id(noteDO.getId())
                            .revision(noteDO.getRevision())
                            .type(noteDO.getType())
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topicId(noteDO.getTopicId())
                            .topicName(noteDO.getTopicName())
                            .creatorId(noteDO.getCreatorId())
                            .creatorName(findUserByIdRspDTO == null ? null : findUserByIdRspDTO.getNickName())
                            .avatar(findUserByIdRspDTO == null ? null : findUserByIdRspDTO.getAvatar())
                            .videoUri(noteDO.getVideoUri())
                            .updateTime(noteDO.getUpdateTime())
                            .visible(noteDO.getVisible())
                            .build();

                });

        // 获取拼装后的 FindNoteDetailRspVO
        FindNoteDetailRspVO findNoteDetailRspVO = resultFuture.get();

        // 计数随详情 JSON 一起缓存，命中路径免 count Feign（TTL 缩至 30~90s 保新鲜）。
        fillNoteCounts(findNoteDetailRspVO);

        // 异步线程中将笔记详情存入 Redis
        threadPoolTaskExecutor.submit(() -> {
            try {
                // 如果笔记包含正文但正文尚未异步落库完毕（content == null），不写入详情缓存，避免产生长达一分钟的正文空白缓存污染
                if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE) && StringUtils.isBlank(findNoteDetailRspVO.getContent())) {
                    log.warn("笔记正文尚未就绪，跳过详情缓存写入, noteId={}", noteId);
                    return;
                }
                String freshNoteDetailJson = JsonUtils.toJsonString(findNoteDetailRspVO);
                long expireSeconds = CacheTtl.basePlusRandom(30, 60);
                stringRedisTemplate.opsForValue().set(noteDetailRedisKey, freshNoteDetailJson, expireSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis 不可用，笔记详情缓存写入失败，响应将继续返回，noteId={}", noteId, e);
            }
        });

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

        Set<String> newMediaUrls = new HashSet<>();
        if (StringUtils.isNotBlank(media.imgUris())) {
            newMediaUrls.addAll(Arrays.asList(StringUtils.split(media.imgUris(), ',')));
        }
        if (StringUtils.isNotBlank(media.videoUri())) {
            newMediaUrls.add(media.videoUri());
        }
        List<String> obsoleteMediaUrls = getMediaUrls(selectNoteDO).stream()
                .filter(url -> !newMediaUrls.contains(url))
                .toList();
        if (CollUtil.isNotEmpty(obsoleteMediaUrls)) {
            ossRpcService.deleteFiles(obsoleteMediaUrls);
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
        // 笔记 ID
        Long noteId = deleteNoteReqVO.getId();

        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许删除笔记
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 逻辑删除
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
            ossRpcService.deleteFiles(mediaUrls);
        }

        return Response.success();
    }

    private List<String> getMediaUrls(NoteDO noteDO) {
        List<String> mediaUrls = new ArrayList<>();
        if (StringUtils.isNotBlank(noteDO.getImgUris())) {
            mediaUrls.addAll(Arrays.asList(StringUtils.split(noteDO.getImgUris(), ',')));
        }
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

        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许修改笔记权限
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 构建更新 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(visible)
                .updateTime(LocalDateTime.now())
                .build();

        NoteChangedEventMqDTO event = NoteChangedEventMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .changeType(NoteOperateEnum.UPDATE.getCode())
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
        // 笔记 ID
        Long noteId = topNoteReqVO.getId();
        // 是否置顶
        Boolean isTop = topNoteReqVO.getIsTop();

        Long currUserId = LoginUserContextHolder.getUserId();

        // 置顶改变频道排序，需按频道 ID bump 版本。
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 构建置顶/取消置顶 DO 实体类
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
            throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
        }

        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
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
            throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
        }

        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
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

        // 已登录的用户 ID
        Long currUserId = LoginUserContextHolder.getUserId();

        // 默认未点赞、未收藏
        boolean isLiked = false;
        boolean isCollected = false;

        // 若当前用户已登录
        if (Objects.nonNull((currUserId))) {
            // 1. 校验是否点赞
            isLiked = checkNoteIsLiked(noteId, currUserId);

            // 2. 校验是否收藏
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
        // 目标用户ID
        Long userId = findPublishedNoteListReqVO.getUserId();
        // 游标
        Long cursor = findPublishedNoteListReqVO.getCursor();
        boolean includePrivate = Objects.equals(LoginUserContextHolder.getUserId(), userId);

        // 返参 VO
        FindPublishedNoteListRspVO findPublishedNoteListRspVO = null;

        // 优先查询缓存
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(userId);
        // 若游标为空，表示查询的是第一页
        if (!includePrivate && Objects.isNull(cursor)) {
            String publishedNoteListJson = stringRedisTemplate.opsForValue().get(publishedNoteListRedisKey);

            if (StringUtils.isNotBlank(publishedNoteListJson)) {
                try {
                    log.info("已发布笔记列表命中了 Redis 缓存...");
                    // Json 字符串转 VO 集合
                    List<NoteItemRspVO> noteItemRspVOS = JsonUtils.parseList(publishedNoteListJson, NoteItemRspVO.class);
                    // 按笔记 ID 降序，最新发布的笔记排最前面
                    List<NoteItemRspVO> sortedList = noteItemRspVOS.stream().sorted(Comparator.comparing(NoteItemRspVO::getNoteId).reversed()).toList();

                    // 实时回填当前用户点赞状态（计数复用快照内嵌基准值，零 Feign 零 Hash 往返）
                    batchGetAndSetNoteIsLiked(sortedList);

                    // 过滤出最早发布的笔记 ID，充当下一页的游标
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

        // 缓存无，则查询数据库
        List<NoteDO> noteDOS = noteDOMapper.selectPublishedNoteListByUserIdAndCursor(userId, cursor, includePrivate);

        if (CollUtil.isNotEmpty(noteDOS)) {
            // DO 转 VO
            List<NoteItemRspVO> noteVOS = noteDOS.stream()
                    .map(noteDO -> {
                        // 获取封面图片
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

            // Feign 调用用户服务，获取博主的用户头像、昵称
            CompletableFuture<FindUserByIdRspDTO> userFuture = CompletableFuture
                    .supplyAsync(() -> {
                        Optional<Long> creatorIdOptional = noteDOS.stream().map(NoteDO::getCreatorId).findAny();
                        return userClient.findById(creatorIdOptional.get());
                    }, threadPoolTaskExecutor);

            // Feign 调用计数服务，批量获取笔记点赞数
            CompletableFuture<List<FindNoteCountsByIdRspDTO>> noteCountFuture = CompletableFuture
                    .supplyAsync(() -> {
                        List<Long> noteIds = noteDOS.stream().map(NoteDO::getId).toList();
                        return countClient.findByNoteIds(noteIds);
                    }, threadPoolTaskExecutor);

            try {
                FindUserByIdRspDTO findUserByIdRspDTO = userFuture.get();
                List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = noteCountFuture.get();

                if (Objects.nonNull(findUserByIdRspDTO)) {
                    // 循环 VO 集合，分别设置头像、昵称
                    noteVOS.forEach(noteItemRspVO -> {
                        noteItemRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                        noteItemRspVO.setNickname(findUserByIdRspDTO.getNickName());
                    });
                }

                // 设置笔记的点赞量
                setVOListLikeTotal(noteVOS, findNoteCountsByIdRspDTOS);

                // 批量获取笔记的点赞状态
                batchGetAndSetNoteIsLiked(noteVOS);
            } catch (Exception e) {
                log.error("## 并发调用错误: ", e);
            }

            // 过滤出最早发布的笔记 ID，充当下一页的游标
            Optional<Long> earliestNoteId = noteDOS.stream().map(NoteDO::getId).min(Long::compareTo);

            findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                    .notes(noteVOS)
                    .nextCursor(earliestNoteId.orElse(null))
                    .build();

            // 同步第一页已发布笔记到 Redis
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
     * @param noteItemRspVOS
     */
    private void batchGetAndSetNoteIsLiked(List<NoteItemRspVO> noteItemRspVOS) {
        // 当前登录用户的 ID
        Long loginUserId = LoginUserContextHolder.getUserId();
        // 若用户已登录
        if (Objects.nonNull(loginUserId)) {
            // 提取所有需要获取点赞状态的笔记 ID
            List<Long> noteIds = noteItemRspVOS.stream().map(NoteItemRspVO::getNoteId).toList();
            Set<Long> likedNoteIds = noteInteractionCacheService.findLikedNoteIds(loginUserId, noteIds);
            noteItemRspVOS.forEach(note -> note.setIsLiked(likedNoteIds.contains(note.getNoteId())));
        }
    }

    /**
     * 如果是博主本人，需要调用计数服务，获取最新的点赞数据
     * @param userId
     * @param sortedList
     */
    private void getAndSetLatestLikeTotalIfAuthor(Long userId, List<NoteItemRspVO> sortedList) {
        Long loginUserId = LoginUserContextHolder.getUserId();
        // 用户已登录，并且查询的是自己
        if (Objects.nonNull(loginUserId) && Objects.equals(loginUserId, userId)) {
            List<Long> noteIds = sortedList.stream().map(NoteItemRspVO::getNoteId).toList();
            List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = countClient.findByNoteIds(noteIds);

            // 设置笔记的点赞量
            setVOListLikeTotal(sortedList, findNoteCountsByIdRspDTOS);
        }
    }

    /**
     * 设置 VO 集合中每篇笔记的点赞量
     * @param noteItemRspVOS
     * @param findNoteCountsByIdRspDTOS
     */
    private static void setVOListLikeTotal(List<NoteItemRspVO> noteItemRspVOS, List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS) {
        if (CollUtil.isNotEmpty(findNoteCountsByIdRspDTOS)) {
            // DTO 集合转 Map
            Map<Long, FindNoteCountsByIdRspDTO> noteIdAndDTOMap = findNoteCountsByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId, dto -> dto));

            // 循环设置 VO 集合，设置每篇笔记的点赞量
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
        // 异步同步缓存
        threadPoolTaskExecutor.submit(() -> {
            try {
                // 过期时间，一小时以内（保底30分钟+随机秒数）
                long expireSeconds = CacheTtl.minutes(30, 30);
                stringRedisTemplate.opsForValue()
                        .set(publishedNoteListRedisKey, JsonUtils.toJsonString(noteVOS), expireSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis 不可用，已发布笔记列表缓存写入失败", e);
            }
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

    private boolean needsCountRefresh(FindNoteDetailRspVO noteDetail) {
        return noteDetail == null || noteDetail.getLikeTotal() == null
                || noteDetail.getCollectTotal() == null || noteDetail.getCommentTotal() == null;
    }

    private void fillNoteCounts(FindNoteDetailRspVO noteDetail) {
        if (noteDetail == null || noteDetail.getId() == null) {
            return;
        }
        if (!needsCountRefresh(noteDetail)) {
            return;
        }
        Long noteId = noteDetail.getId();
        try {
            String countKey = "count:note:" + noteId;
            List<Object> hashValues = stringRedisTemplate.opsForHash().multiGet(countKey,
                    List.of("likeTotal", "collectTotal", "commentTotal"));
            if (CollUtil.isNotEmpty(hashValues) && hashValues.size() >= 3
                    && hashValues.get(0) != null && hashValues.get(1) != null && hashValues.get(2) != null) {
                noteDetail.setLikeTotal(Long.parseLong(String.valueOf(hashValues.get(0))));
                noteDetail.setCollectTotal(Long.parseLong(String.valueOf(hashValues.get(1))));
                noteDetail.setCommentTotal(Long.parseLong(String.valueOf(hashValues.get(2))));
                return;
            }
        } catch (Exception e) {
            log.warn("从 Redis 读取笔记计数失败，降级调用 count RPC, noteId={}", noteId, e);
        }

        try {
            List<FindNoteCountsByIdRspDTO> counts = countClient.findByNoteIds(List.of(noteId));
            FindNoteCountsByIdRspDTO count = CollUtil.isEmpty(counts) ? null : counts.get(0);
            noteDetail.setLikeTotal(count == null || count.getLikeTotal() == null ? 0L : count.getLikeTotal());
            noteDetail.setCollectTotal(count == null || count.getCollectTotal() == null ? 0L : count.getCollectTotal());
            noteDetail.setCommentTotal(count == null || count.getCommentTotal() == null ? 0L : count.getCommentTotal());
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
        NoteAccessSnapshot cachedSnapshot = readAccessSnapshot(key);
        if (cachedSnapshot != null) {
            return cachedSnapshot;
        }

        // 热点笔记被击穿时单飞重建：抢锁者回源写回，抢不到者轮询等待。
        String lockKey = RedisKeyConstants.buildNoteAccessRebuildLockKey(noteId);
        RLock lock;
        try {
            lock = tryAcquireRebuildLock(lockKey, ACCESS_SNAPSHOT_REBUILD_LOCK_SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，笔记访问快照重建锁获取失败，回源 MySQL，noteId={}", noteId, e);
            return loadAccessSnapshotFromMySql(noteId, key, false);
        }
        if (lock == null) {
            try {
                NoteAccessSnapshot rebuilt = waitForAccessSnapshot(key);
                if (rebuilt != null) {
                    return rebuilt;
                }
            } catch (Exception e) {
                log.warn("Redis 不可用，笔记访问快照轮询失败，回源 MySQL，noteId={}", noteId, e);
            }
            // 轮询超时兜底：查库不写回，写回由锁持有者负责。
            return loadAccessSnapshotFromMySql(noteId, key, false);
        }
        try {
            // 二次检查：抢锁期间其他重建者可能已写入。
            NoteAccessSnapshot reRead = readAccessSnapshot(key);
            if (reRead != null) {
                return reRead;
            }
            return loadAccessSnapshotFromMySql(noteId, key, true);
        } finally {
            releaseRebuildLock(lock, lockKey);
        }
    }

    /** 读取访问快照缓存；空白/"null"/解析失败统一走重建（解析失败先删脏值）。 */
    private NoteAccessSnapshot readAccessSnapshot(String key) {
        String cached = getAccessSnapshotCacheValue(key);
        if (StringUtils.isBlank(cached)) {
            return null;
        }
        if ("null".equals(cached)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(cached, NoteAccessSnapshot.class);
        } catch (Exception e) {
            log.warn("笔记访问快照解析失败，跳过缓存并回源 MySQL，key={}", key, e);
            deleteAccessSnapshotCache(key);
            return null;
        }
    }

    private NoteAccessSnapshot waitForAccessSnapshot(String key) {
        for (int i = 0; i < ACCESS_SNAPSHOT_REBUILD_RETRY_TIMES; i++) {
            sleepBeforeAccessSnapshotRetry();
            NoteAccessSnapshot snapshot = readAccessSnapshot(key);
            if (snapshot != null) {
                return snapshot;
            }
            // "null" 哨兵已折叠为 null；哨兵写入也视为重建完成。
            String cached = getAccessSnapshotCacheValue(key);
            if ("null".equals(cached)) {
                return null;
            }
        }
        return null;
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

    private RLock tryAcquireRebuildLock(String lockKey, long leaseSeconds) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock == null) {
            return null;
        }
        try {
            return lock.tryLock(0, leaseSeconds, TimeUnit.SECONDS) ? lock : null;
        } catch (Exception e) {
            throw new IllegalStateException("Redis 不可用，笔记访问快照重建锁获取失败, lockKey=" + lockKey, e);
        }
    }

    private void releaseRebuildLock(RLock lock, String lockKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，笔记访问快照重建锁释放失败，key={}", lockKey, e);
        }
    }

    private void sleepBeforeAccessSnapshotRetry() {
        try {
            Thread.sleep(ACCESS_SNAPSHOT_REBUILD_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 批量获取笔记访问控制快照。Redis 冷缓存时只进行一次 IN 查询，避免批量评论校验退化为 N 次主键查询。
     */
    private Map<Long, NoteAccessSnapshot> loadAccessSnapshots(List<Long> noteIds) {
        List<String> keys = noteIds.stream()
                .map(RedisKeyConstants::buildNoteAccessKey)
                .toList();
        List<String> cachedValues;
        try {
            cachedValues = stringRedisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn("Redis 不可用，笔记访问快照批量读取失败，回源 MySQL，noteIds={}", noteIds, e);
            return loadAccessSnapshotsFromMySql(noteIds);
        }
        Map<Long, NoteAccessSnapshot> snapshots = new HashMap<>(noteIds.size());
        List<Long> missedNoteIds = new ArrayList<>();

        for (int i = 0; i < noteIds.size(); i++) {
            String cached = cachedValues == null || cachedValues.size() <= i ? null : cachedValues.get(i);
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
                // 损坏的快照不能阻断批量权限校验；删除后统一回源并回填。
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
                .collect(Collectors.toMap(NoteDO::getId, this::toAccessSnapshot));
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
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，笔记访问快照读取失败，回源 MySQL，key={}", key, e);
            return null;
        }
    }

    private void cacheAccessSnapshot(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，笔记访问快照写入失败，响应将继续返回，key={}", key, e);
        }
    }

    private void deleteAccessSnapshotCache(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，笔记访问快照删除失败，key={}", key, e);
        }
    }

    private NoteAccessSnapshot toAccessSnapshot(NoteDO note) {
        NoteAccessSnapshot snapshot = new NoteAccessSnapshot();
        snapshot.setCreatorId(note.getCreatorId());
        snapshot.setVisible(note.getVisible());
        snapshot.setRevision(note.getRevision());
        return snapshot;
    }

}
