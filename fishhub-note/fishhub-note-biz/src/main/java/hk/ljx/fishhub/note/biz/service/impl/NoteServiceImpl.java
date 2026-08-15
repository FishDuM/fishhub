package hk.ljx.fishhub.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
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
import hk.ljx.fishhub.note.biz.model.dto.NoteOperateMqDTO;
import hk.ljx.fishhub.note.biz.model.dto.NoteContentTaskMqDTO;
import hk.ljx.fishhub.note.biz.model.bo.NoteAccessSnapshot;
import hk.ljx.fishhub.note.biz.model.vo.*;
import hk.ljx.fishhub.note.biz.rpc.CountRpcService;
import hk.ljx.fishhub.note.biz.rpc.DistributedIdGeneratorRpcService;
import hk.ljx.fishhub.note.biz.rpc.KeyValueRpcService;
import hk.ljx.fishhub.note.biz.rpc.OssRpcService;
import hk.ljx.fishhub.note.biz.rpc.UserRpcService;
import hk.ljx.fishhub.note.biz.retry.ReliableMqOutbox;
import hk.ljx.fishhub.note.biz.service.NoteService;
import hk.ljx.fishhub.note.biz.service.NotePersistenceService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.note.biz.service.UserNoteListService;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
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
public class NoteServiceImpl implements NoteService {

    private static final long ZSET_NOT_INITIALIZED = -1L;

    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private TopicDOMapper topicDOMapper;
    @Resource
    private ChannelDOMapper channelDOMapper;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private UserRpcService userRpcService;
    @Resource(name = "fishhubTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Resource
    private CountRpcService countRpcService;
    @Resource
    private ReliableMqOutbox reliableMqOutbox;
    @Resource
    private NotePersistenceService notePersistenceService;
    @Resource
    private NoteInteractionCacheService noteInteractionCacheService;
    @Resource
    private UserNoteListService userNoteListService;
    @Resource
    private OssRpcService ossRpcService;

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
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
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
        String snowflakeIdId = distributedIdGeneratorRpcService.getSnowflakeId();
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
                .id(Long.valueOf(snowflakeIdId))
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
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(creatorId)
                .noteId(noteDO.getId())
                .type(NoteOperateEnum.PUBLISH.getCode()) // 发布笔记
                .build();

        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_PUBLISH;
        String eventBody = JsonUtils.toJsonString(noteOperateMqDTO);

        String contentTaskBody = StringUtils.isBlank(content) ? null
                : buildNoteContentTask(noteDO.getId(), noteDO.getContentUuid(), content, NoteContentTaskTypeEnum.UPSERT);

        // 笔记元数据、正文任务与发布事件在同一个 MySQL 事务中提交。
        notePersistenceService.savePublishedNote(noteDO, destination, eventBody, contentTaskBody);

        // 事务提交后即时投递；失败时由 outbox 定时补发。
        if (contentTaskBody != null) {
            reliableMqOutbox.sendNow(MQConstants.TOPIC_SYNC_NOTE_CONTENT, contentTaskBody);
        }
        reliableMqOutbox.sendNow(MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE, eventBody);
        reliableMqOutbox.sendNow(destination, eventBody);
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
                fillNoteCounts(findNoteDetailRspVO);
                return Response.success(findNoteDetailRspVO);
            }
            LOCAL_CACHE.invalidate(noteId);
        }

        // 从 Redis 缓存中获取
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);

        // 若缓存中有该笔记的数据，则直接返回
        if (StringUtils.isNotBlank(noteDetailJson)) {
            if ("null".equals(noteDetailJson)) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            if (!isCurrentAndAccessible(noteId, userId, findNoteDetailRspVO)) {
                redisTemplate.delete(noteDetailRedisKey);
            } else {
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(noteId,
                        Objects.isNull(findNoteDetailRspVO) ? "null" : JsonUtils.toJsonString(findNoteDetailRspVO));
            });
            fillNoteCounts(findNoteDetailRspVO);
            return Response.success(findNoteDetailRspVO);
            }
        }

        // 若 Redis 缓存中获取不到，则走数据库查询
        // 查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 若该笔记不存在，则抛出业务异常
        if (Objects.isNull(noteDO)) {
            threadPoolTaskExecutor.execute(() -> {
                // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(noteDetailRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 可见性校验
        checkNoteVisible(noteDO.getVisible(), userId, noteDO.getCreatorId());

        // 并发查询优化
        // RPC: 调用用户服务
        CompletableFuture<FindUserByIdRspDTO> userResultFuture = CompletableFuture
                .supplyAsync(() -> userRpcService.findById(noteDO.getCreatorId()), threadPoolTaskExecutor);

        // RPC: 调用 K-V 存储服务获取内容
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE)) {
            contentResultFuture = CompletableFuture
                    .supplyAsync(() -> keyValueRpcService.findNoteContent(noteDO.getContentUuid()), threadPoolTaskExecutor);
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

        // 异步线程中将笔记详情存入 Redis
        threadPoolTaskExecutor.submit(() -> {
            String noteDetailJson1 = JsonUtils.toJsonString(findNoteDetailRspVO);
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            redisTemplate.opsForValue().set(noteDetailRedisKey, noteDetailJson1, expireSeconds, TimeUnit.SECONDS);
        });

        fillNoteCounts(findNoteDetailRspVO);
        return Response.success(findNoteDetailRspVO);
    }

    /**
     * 笔记更新
     *
     * @param updateNoteReqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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

        if (noteDOMapper.updateByPrimaryKeyAndRevision(noteDO) != 1) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        List<String> contentTaskBodies = new ArrayList<>();
        if (content.createdNewContent()) {
            contentTaskBodies.add(buildNoteContentTask(noteId, newContentUuid, content.value(), NoteContentTaskTypeEnum.UPSERT));
        }
        if (StringUtils.isNotBlank(oldContentUuid) && !Objects.equals(oldContentUuid, newContentUuid)) {
            contentTaskBodies.add(buildNoteContentTask(noteId, oldContentUuid, null, NoteContentTaskTypeEnum.DELETE));
        }
        contentTaskBodies.forEach(body -> reliableMqOutbox.enqueue(MQConstants.TOPIC_SYNC_NOTE_CONTENT, body));
        if (CollUtil.isNotEmpty(contentTaskBodies)) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    contentTaskBodies.forEach(body -> reliableMqOutbox.sendNow(MQConstants.TOPIC_SYNC_NOTE_CONTENT, body));
                }
            });
        }

        enqueueNoteCacheInvalidation(noteId, currUserId);

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
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ossRpcService.deleteFiles(obsoleteMediaUrls);
                }
            });
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
    @Transactional(rollbackFor = Exception.class)
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

        if (noteDOMapper.logicalDeleteByPrimaryKeyAndRevision(noteDO) != 1) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        String contentTaskBody = StringUtils.isBlank(selectNoteDO.getContentUuid()) ? null
                : buildNoteContentTask(noteId, selectNoteDO.getContentUuid(), null, NoteContentTaskTypeEnum.DELETE);
        if (contentTaskBody != null) {
            reliableMqOutbox.enqueue(MQConstants.TOPIC_SYNC_NOTE_CONTENT, contentTaskBody);
        }

        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .type(NoteOperateEnum.DELETE.getCode()) // 删除笔记
                .build();

        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_DELETE;
        String eventBody = JsonUtils.toJsonString(noteOperateMqDTO);
        // 删除事实与所有后续事件一起提交，避免提交前缓存被重新填充。
        reliableMqOutbox.enqueue(destination, eventBody);
        enqueueNoteCacheInvalidation(noteId, currUserId);

        List<String> mediaUrls = getMediaUrls(selectNoteDO);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reliableMqOutbox.sendNow(destination, eventBody);
                if (contentTaskBody != null) {
                    reliableMqOutbox.sendNow(MQConstants.TOPIC_SYNC_NOTE_CONTENT, contentTaskBody);
                }
                if (CollUtil.isNotEmpty(mediaUrls)) {
                    ossRpcService.deleteFiles(mediaUrls);
                }
            }
        });

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

    private String buildNoteContentTask(Long noteId, String contentUuid, String content, NoteContentTaskTypeEnum type) {
        return JsonUtils.toJsonString(NoteContentTaskMqDTO.builder()
                .noteId(noteId)
                .contentUuid(contentUuid)
                .content(content)
                .type(type.name())
                .build());
    }

    /**
     * 笔记仅对自己可见
     *
     * @param updateNoteVisibleOnlyMeReqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        return updateVisibility(UpdateNoteVisibilityReqVO.builder()
                .id(updateNoteVisibleOnlyMeReqVO.getId())
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        int count = noteDOMapper.updateVisibility(noteDO);

        // 若影响的行数为 0，则表示该笔记无法修改可见性
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }

        enqueueNoteCacheInvalidation(noteId, currUserId);

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

        enqueueNoteCacheInvalidation(noteId, currUserId);

        return Response.success();
    }

    private void enqueueNoteCacheInvalidation(Long noteId, Long creatorId) {
        NoteOperateMqDTO cacheEvent = NoteOperateMqDTO.builder()
                .creatorId(creatorId)
                .noteId(noteId)
                .build();
        String localCacheEventBody = String.valueOf(noteId);
        String redisCacheEventBody = JsonUtils.toJsonString(cacheEvent);

        reliableMqOutbox.enqueue(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, localCacheEventBody);
        reliableMqOutbox.enqueue(MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE, redisCacheEventBody);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reliableMqOutbox.sendNow(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, localCacheEventBody);
                reliableMqOutbox.sendNow(MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE, redisCacheEventBody);
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

        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);

        LocalDateTime now = LocalDateTime.now();
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_like_check_and_update_zset.lua")));
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));

        // 若 ZSet 列表不存在，需要重新初始化
        if (Objects.equals(result, ZSET_NOT_INITIALIZED)) {
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);

            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
            script2.setResultType(Long.class);

            // 若数据库中存在点赞记录，需要批量同步
            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);

                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs);

                // 再次调用 note_like_check_and_update_zset.lua 脚本，将点赞的笔记添加到 zset 中
                redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
            } else { // 若数据库中，无点赞的笔记记录，则直接将当前点赞的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now())); // score
                luaArgs.add(noteId); // 当前点赞的笔记 ID
                luaArgs.add(expireSeconds); // 随机过期时间

                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs.toArray());
            }
        }

        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.LIKE.getCode()) // 点赞笔记
                .createTime(now)
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            noteInteractionCacheService.evictLikeCaches(userId);
            throw new IllegalStateException("笔记点赞消息发送失败", e);
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

        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);

        redisTemplate.opsForZSet().remove(userNoteLikeZSetKey, noteId);

        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.UNLIKE.getCode()) // 取消点赞笔记
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            noteInteractionCacheService.evictLikeCaches(userId);
            throw new IllegalStateException("笔记取消点赞消息发送失败", e);
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

        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);

        LocalDateTime now = LocalDateTime.now();
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_collect_check_and_update_zset.lua")));
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, Collections.singletonList(userNoteCollectZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));

        // 若 ZSet 列表不存在，需要重新初始化
        if (Objects.equals(result, ZSET_NOT_INITIALIZED)) {
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);

            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
            script2.setResultType(Long.class);

            // 若数据库中存在已收藏笔记记录，需要批量同步
            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                Object[] luaArgs = buildNoteCollectZSetLuaArgs(noteCollectionDOS, expireSeconds);

                redisTemplate.execute(script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs);

                // 再次调用 note_collect_check_and_update_zset.lua 脚本，将当前收藏的笔记添加到 zset 中
                redisTemplate.execute(script, Collections.singletonList(userNoteCollectZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
            } else { // 若数据库中，未收藏任何笔记，则直接将当前收藏的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now())); // score 收藏时间
                luaArgs.add(noteId); // 当前收藏的笔记 ID
                luaArgs.add(expireSeconds); // 随机过期时间

                redisTemplate.execute(script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs.toArray());
            }
        }

        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.COLLECT.getCode()) // 收藏笔记
                .createTime(now)
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(collectUnCollectNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_COLLECT;

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            noteInteractionCacheService.evictCollectCaches(userId);
            throw new IllegalStateException("笔记收藏消息发送失败", e);
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

        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);

        redisTemplate.opsForZSet().remove(userNoteCollectZSetKey, noteId);

        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.UN_COLLECT.getCode()) // 取消收藏笔记
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unCollectNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_UN_COLLECT;

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            noteInteractionCacheService.evictCollectCaches(userId);
            throw new IllegalStateException("笔记取消收藏消息发送失败", e);
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
            String publishedNoteListJson = redisTemplate.opsForValue().get(publishedNoteListRedisKey);

            if (StringUtils.isNotBlank(publishedNoteListJson)) {
                try {
                    log.info("## 已发布笔记列表命中了 Redis 缓存...");
                    // Json 字符串转 VO 集合
                    List<NoteItemRspVO> noteItemRspVOS = JsonUtils.parseList(publishedNoteListJson, NoteItemRspVO.class);
                    // 按笔记 ID 降序，最新发布的笔记排最前面
                    List<NoteItemRspVO> sortedList = noteItemRspVOS.stream().sorted(Comparator.comparing(NoteItemRspVO::getNoteId).reversed()).toList();

                    // 过滤出最早发布的笔记 ID，充当下一页的游标
                    Optional<Long> earliestNoteId = noteItemRspVOS.stream().map(NoteItemRspVO::getNoteId).min(Long::compareTo);

                    // 如果是博主本人，需要调用计数服务，获取最新的点赞数据
                    getAndSetLatestLikeTotalIfAuthor(userId, sortedList);

                    // 批量获取笔记的点赞状态
                    batchGetAndSetNoteIsLiked(sortedList);

                    findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                            .notes(sortedList)
                            .nextCursor(earliestNoteId.orElse(null))
                            .build();
                    return Response.success(findPublishedNoteListRspVO);
                } catch (Exception e) {
                    log.error("", e);
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
                        return userRpcService.findById(creatorIdOptional.get());
                    }, threadPoolTaskExecutor);

            // Feign 调用计数服务，批量获取笔记点赞数
            CompletableFuture<List<FindNoteCountsByIdRspDTO>> noteCountFuture = CompletableFuture
                    .supplyAsync(() -> {
                        List<Long> noteIds = noteDOS.stream().map(NoteDO::getId).toList();
                        return countRpcService.findByNoteIds(noteIds);
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
            List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = countRpcService.findByNoteIds(noteIds);

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
            // 过期时间，一小时以内（保底30分钟+随机秒数）
            long expireSeconds = 60*30 + RandomUtil.randomInt(60*30);
            redisTemplate.opsForValue()
                    .set(publishedNoteListRedisKey, JsonUtils.toJsonString(noteVOS), expireSeconds, TimeUnit.SECONDS);
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
     * 构建笔记收藏 ZSET Lua 脚本参数
     *
     * @param noteCollectionDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildNoteCollectZSetLuaArgs(List<NoteCollectionDO> noteCollectionDOS, long expireSeconds) {
        int argsLength = noteCollectionDOS.size() * 2 + 1; // 每个笔记收藏关系有 2 个参数（score 和 value），最后再跟一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteCollectionDO noteCollectionDO : noteCollectionDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(noteCollectionDO.getCreateTime()); // 收藏时间作为 score
            luaArgs[i + 1] = noteCollectionDO.getNoteId();          // 笔记ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    /**
     * 构建笔记点赞 ZSET Lua 脚本参数
     *
     * @param noteLikeDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildNoteLikeZSetLuaArgs(List<NoteLikeDO> noteLikeDOS, long expireSeconds) {
        int argsLength = noteLikeDOS.size() * 2 + 1; // 每个笔记点赞关系有 2 个参数（score 和 value），最后再跟一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteLikeDO noteLikeDO : noteLikeDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(noteLikeDO.getCreateTime()); // 点赞时间作为 score
            luaArgs[i + 1] = noteLikeDO.getNoteId();          // 笔记ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
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

    private void fillNoteCounts(FindNoteDetailRspVO noteDetail) {
        try {
            List<FindNoteCountsByIdRspDTO> counts = countRpcService.findByNoteIds(List.of(noteDetail.getId()));
            FindNoteCountsByIdRspDTO count = CollUtil.isEmpty(counts) ? null : counts.get(0);
            noteDetail.setLikeTotal(count == null || count.getLikeTotal() == null ? 0L : count.getLikeTotal());
            noteDetail.setCollectTotal(count == null || count.getCollectTotal() == null ? 0L : count.getCollectTotal());
            noteDetail.setCommentTotal(count == null || count.getCommentTotal() == null ? 0L : count.getCommentTotal());
        } catch (Exception e) {
            log.warn("查询笔记计数失败，noteId={}", noteDetail.getId(), e);
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
                && !Objects.equals(currUserId, creatorId)) { // 仅自己可见, 并且访问用户为笔记创建者
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
        String cached = getAccessSnapshotCacheValue(key);
        if (StringUtils.isNotBlank(cached)) {
            if ("null".equals(cached)) {
                return null;
            }
            try {
                return JsonUtils.parseObject(cached, NoteAccessSnapshot.class);
            } catch (Exception e) {
                log.warn("笔记访问快照解析失败，跳过缓存并回源 MySQL，key={}", key, e);
                deleteAccessSnapshotCache(key);
            }
        }
        NoteDO note = noteDOMapper.selectAccessInfoByNoteId(noteId);
        if (note == null) {
            cacheAccessSnapshot(key, "null");
            return null;
        }
        NoteAccessSnapshot snapshot = toAccessSnapshot(note);
        cacheAccessSnapshot(key, JsonUtils.toJsonString(snapshot));
        return snapshot;
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
            cachedValues = redisTemplate.opsForValue().multiGet(keys);
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
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，笔记访问快照读取失败，回源 MySQL，key={}", key, e);
            return null;
        }
    }

    private void cacheAccessSnapshot(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，笔记访问快照写入失败，响应将继续返回，key={}", key, e);
        }
    }

    private void deleteAccessSnapshotCache(String key) {
        try {
            redisTemplate.delete(key);
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
