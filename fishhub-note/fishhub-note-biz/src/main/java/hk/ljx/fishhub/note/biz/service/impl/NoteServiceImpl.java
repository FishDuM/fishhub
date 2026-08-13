package hk.ljx.fishhub.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.common.util.NumberUtils;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
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
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");

                break;
            case VIDEO: // 视频笔记
                videoUri = publishNoteReqVO.getVideoUri();
                // 校验视频链接是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
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
        }

        Long channelId = publishNoteReqVO.getChannelId();
        ChannelDO channel = channelDOMapper.selectByPrimaryKey(channelId);
        if (Objects.isNull(channel) || Boolean.TRUE.equals(channel.getIsDeleted())) {
            throw new IllegalArgumentException("频道不存在");
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
                .build();

        // 正文属于 KV 存储，笔记元数据与后续事件由本地事务和 Outbox 保证一致。
        if (StringUtils.isBlank(content)) {
            persistPublishedNote(creatorId, noteDO);
            return Response.success();
        }

        if (!keyValueRpcService.saveNoteContent(contentUuid, content)) {
            throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
        }
        try {
            persistPublishedNote(creatorId, noteDO);
        } catch (Exception e) {
            if (!keyValueRpcService.deleteNoteContent(contentUuid)) {
                log.error("笔记发布回滚时无法删除正文，noteId={}, contentUuid={}", noteDO.getId(), contentUuid);
            }
            throw e;
        }

        return Response.success();
    }

    /**
     * 将笔记元数据和发布事件一起持久化。
     * @param creatorId
     * @param noteDO
     */
    private void persistPublishedNote(Long creatorId, NoteDO noteDO) {
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(creatorId)
                .noteId(noteDO.getId())
                .type(NoteOperateEnum.PUBLISH.getCode()) // 发布笔记
                .build();

        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_PUBLISH;
        String eventBody = JsonUtils.toJsonString(noteOperateMqDTO);

        // 笔记元数据与发布事件在同一个 MySQL 事务中提交。
        notePersistenceService.savePublishedNote(noteDO, destination, eventBody);

        // 事务提交后即时投递；失败时由 outbox 定时补发。
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

        // 先从本地缓存中查询
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        if (StringUtils.isNotBlank(findNoteDetailRspVOStrLocalCache)) {
            if ("null".equals(findNoteDetailRspVOStrLocalCache)) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
            log.info("==> 命中了本地缓存；{}", findNoteDetailRspVOStrLocalCache);
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);
            return Response.success(findNoteDetailRspVO);
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
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(noteId,
                        Objects.isNull(findNoteDetailRspVO) ? "null" : JsonUtils.toJsonString(findNoteDetailRspVO));
            });
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);

            return Response.success(findNoteDetailRspVO);
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
        Integer visible = noteDO.getVisible();
        checkNoteVisible(visible, userId, noteDO.getCreatorId());

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
                            .type(noteDO.getType())
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topicId(noteDO.getTopicId())
                            .topicName(noteDO.getTopicName())
                            .creatorId(noteDO.getCreatorId())
                            .creatorName(findUserByIdRspDTO.getNickName())
                            .avatar(findUserByIdRspDTO.getAvatar())
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
        // 笔记 ID
        Long noteId = updateNoteReqVO.getId();
        // 笔记类型
        Integer type = updateNoteReqVO.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = updateNoteReqVO.getImgUris();
                // 校验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");

                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO: // 视频笔记
                videoUri = updateNoteReqVO.getVideoUri();
                // 校验视频链接是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }


        Long currUserId = LoginUserContextHolder.getUserId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 笔记不存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许更新笔记
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 更新 SQL 是全字段更新；未传字段必须保留原值。
        Long channelId = Objects.requireNonNullElse(updateNoteReqVO.getChannelId(), selectNoteDO.getChannelId());
        ChannelDO channelDO = channelDOMapper.selectByPrimaryKey(channelId);
        if (Objects.isNull(channelDO) || Boolean.TRUE.equals(channelDO.getIsDeleted())) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        // 话题
        Long topicId = updateNoteReqVO.getTopicId() != null
                ? updateNoteReqVO.getTopicId()
                : selectNoteDO.getTopicId();
        String topicName = selectNoteDO.getTopicName();
        if (Objects.nonNull(topicId)) {
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);

            // 判断一下提交的话题, 是否是真实存在的
            if (StringUtils.isBlank(topicName)) throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
        }

        // 更新笔记元数据表 t_note
        String content = updateNoteReqVO.getContent();
        String oldContentUuid = selectNoteDO.getContentUuid();
        // 正文采用不可变版本：先写新 UUID，再由 MySQL 事务切换引用。
        // 这样数据库回滚时旧正文仍然可读，不会出现元数据与正文跨存储错配。
        String contentUuid = StringUtils.isBlank(content) ? null : UUID.randomUUID().toString();
        if (StringUtils.isNotBlank(content)
                && !keyValueRpcService.saveNoteContent(contentUuid, content)) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        String newContentUuid = contentUuid;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (StringUtils.isNotBlank(oldContentUuid)
                        && !Objects.equals(oldContentUuid, newContentUuid)
                        && !keyValueRpcService.deleteNoteContent(oldContentUuid)) {
                    log.error("旧笔记正文删除失败，noteId={}, contentUuid={}", noteId, oldContentUuid);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED
                        && StringUtils.isNotBlank(newContentUuid)
                        && !keyValueRpcService.deleteNoteContent(newContentUuid)) {
                    log.error("回滚后新笔记正文清理失败，noteId={}, contentUuid={}", noteId, newContentUuid);
                }
            }
        });

        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isContentEmpty(StringUtils.isBlank(content))
                .channelId(channelId)
                .imgUris(imgUris)
                .title(updateNoteReqVO.getTitle())
                .topicId(topicId)
                .topicName(topicName)
                .type(type)
                .updateTime(LocalDateTime.now())
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();

        if (noteDOMapper.updateByPrimaryKey(noteDO) != 1) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        enqueueNoteCacheInvalidation(noteId, currUserId);

        Set<String> newMediaUrls = new HashSet<>();
        if (StringUtils.isNotBlank(imgUris)) {
            newMediaUrls.addAll(Arrays.asList(StringUtils.split(imgUris, ',')));
        }
        if (StringUtils.isNotBlank(videoUri)) {
            newMediaUrls.add(videoUri);
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
                .build();

        noteDOMapper.updateByPrimaryKeySelective(noteDO);

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

    /**
     * 笔记仅对自己可见
     *
     * @param updateNoteVisibleOnlyMeReqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        // 笔记 ID
        Long noteId = updateNoteVisibleOnlyMeReqVO.getId();

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
                .visible(NoteVisibleEnum.PRIVATE.getCode()) // 可见性设置为仅对自己可见
                .updateTime(LocalDateTime.now())
                .build();

        int count = noteDOMapper.updateVisibleOnlyMe(noteDO);

        // 若影响的行数为 0，则表示该笔记无法修改为仅自己可见
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

        // 校验笔记仍然存在，并获取发布者 ID 供计数消息使用
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

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
                .noteCreatorId(creatorId) // 笔记发布者 ID
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            noteInteractionCacheService.evictLikeCache(userId);
            redisTemplate.delete(userNoteLikeZSetKey);
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

        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

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
                .noteCreatorId(creatorId) // 笔记发布者 ID
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            noteInteractionCacheService.evictLikeCache(userId);
            redisTemplate.delete(userNoteLikeZSetKey);
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

        // 校验笔记仍然存在，并获取发布者 ID 供计数消息使用
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

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
                .noteCreatorId(creatorId) // 笔记发布者 ID
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(collectUnCollectNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_COLLECT;

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            noteInteractionCacheService.evictCollectCache(userId);
            redisTemplate.delete(userNoteCollectZSetKey);
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

        // 校验笔记仍然存在，并获取发布者 ID 供计数消息使用
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

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
                .noteCreatorId(creatorId) // 笔记发布者 ID
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unCollectNoteMqDTO))
                .build();

        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_UN_COLLECT;

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            noteInteractionCacheService.evictCollectCache(userId);
            redisTemplate.delete(userNoteCollectZSetKey);
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

        // 返参 VO
        FindPublishedNoteListRspVO findPublishedNoteListRspVO = null;

        // 优先查询缓存
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(userId);
        // 若游标为空，表示查询的是第一页
        if (Objects.isNull(cursor)) {
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
        List<NoteDO> noteDOS = noteDOMapper.selectPublishedNoteListByUserIdAndCursor(userId, cursor);

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

            // 等待所有任务完成，并合并结果
            CompletableFuture.allOf(userFuture, noteCountFuture).join();

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
            if (Objects.isNull(cursor)) {
                syncFirstPagePublishedNoteList2Redis(noteVOS, publishedNoteListRedisKey);
            }
        }

        return Response.success(findPublishedNoteListRspVO);
    }

    @Override
    public Response<FindPublishedNoteListRspVO> findCollectedNoteList(FindPublishedNoteListReqVO request) {
        return userNoteListService.findCollectedNotes(request);
    }

    @Override
    public Response<FindPublishedNoteListRspVO> findLikedNoteList(FindPublishedNoteListReqVO request) {
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
        // 先从本地缓存校验
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        // 解析 Json 字符串为 VO 对象
        FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);

        // 若本地缓存没有
        if (Objects.isNull(findNoteDetailRspVO)) {
            // 再从 Redis 中校验
            String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);

            String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);

            // 解析 Json 字符串为 VO 对象
            findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);

            // 都不存在，再查询数据库校验是否存在
            if (Objects.isNull(findNoteDetailRspVO)) {
                // 笔记发布者用户 ID
                Long creatorId = noteDOMapper.selectCreatorIdByNoteId(noteId);

                // 若数据库中也不存在，提示用户
                if (Objects.isNull(creatorId)) {
                    throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }

                // 若数据库中存在，异步同步一下缓存
                threadPoolTaskExecutor.submit(() -> {
                    FindNoteDetailReqVO findNoteDetailReqVO = FindNoteDetailReqVO.builder().id(noteId).build();
                    findNoteDetail(findNoteDetailReqVO);
                });
                return creatorId;
            }
        }

        return findNoteDetailRspVO.getCreatorId();
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

    /**
     * 校验笔记的可见性（针对 VO 实体类）
     * @param userId
     * @param findNoteDetailRspVO
     */
    private void checkNoteVisibleFromVO(Long userId, FindNoteDetailRspVO findNoteDetailRspVO) {
        if (Objects.nonNull(findNoteDetailRspVO)) {
            Integer visible = findNoteDetailRspVO.getVisible();
            checkNoteVisible(visible, userId, findNoteDetailRspVO.getCreatorId());
        }
    }


}
