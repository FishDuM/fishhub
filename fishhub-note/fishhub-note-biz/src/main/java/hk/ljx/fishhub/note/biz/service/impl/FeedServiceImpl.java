package hk.ljx.fishhub.note.biz.service.impl;

import hk.ljx.framework.common.util.CacheRebuildSupport;
import hk.ljx.framework.common.util.CacheTtl;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.ChannelDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.TopicDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindChannelRspVO;
import hk.ljx.fishhub.note.biz.model.vo.DiscoverNotePageResponse;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNoteListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicRspVO;
import hk.ljx.fishhub.note.biz.model.vo.NoteItemRspVO;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.note.biz.service.FeedService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private static final long PAGE_SIZE = 10L;

    // 冷缓存并发风暴防护：单飞锁持有者重建期间，等待者最长等 1s（20×50ms）拿热缓存，
    // 避免 60ms 等不到就全部回源 MySQL 造成 Feign 扇出风暴（实测 200 并发冷缓存最差 5.2s）。
    private static final int CACHE_REBUILD_RETRY_TIMES = 20;
    private static final long CACHE_REBUILD_RETRY_INTERVAL_MILLIS = 50L;
    private static final long DISCOVER_PAGE_REBUILD_LOCK_SECONDS = 5L;

    private static final Cache<String, List<FindTopicRspVO>> TOPIC_LOCAL_CACHE = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    // 频道列表本地缓存 1min，避免热路径每请求查一次 MySQL。
    private final Cache<String, List<FindChannelRspVO>> channelLocalCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    private final ChannelDOMapper channelDOMapper;
    private final TopicDOMapper topicDOMapper;
    private final NoteDOMapper noteDOMapper;
    private final NoteInteractionCacheService noteInteractionCacheService;
    private final UserClient userClient;
    private final CountClient countClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    @Override
    public Response<List<FindChannelRspVO>> findChannelList() {
        String key = RedisKeyConstants.activeChannelSnapshotKey();
        List<FindChannelRspVO> local = channelLocalCache.getIfPresent(key);
        if (local != null) {
            return Response.success(local);
        }
        Object cached = getRedisValue(key, "频道快照");
        List<FindChannelRspVO> channels;
        if (cached instanceof String cachedJson) {
            try {
                channels = JsonUtils.parseList(cachedJson, FindChannelRspVO.class);
            } catch (Exception e) {
                log.warn("频道快照缓存解析失败，跳过缓存并尝试删除，key={}", key, e);
                channels = null;
                deleteRedisValue(key, "频道快照");
            }
        } else {
            channels = null;
        }
        if (CollUtil.isEmpty(channels)) {
            channels = channelDOMapper.selectAllEnabled().stream()
                    .map(channel -> FindChannelRspVO.builder().id(channel.getId()).name(channel.getName()).build())
                    .toList();
            cacheChannels(key, channels);
        }
        channelLocalCache.put(key, channels);
        return Response.success(channels);
    }

    @Override
    public DiscoverNotePageResponse<NoteItemRspVO> findDiscoverNoteList(FindDiscoverNoteListReqVO request) {
        Long cursor = (request.getCursor() != null && request.getCursor() > 0) ? request.getCursor() : null;
        return findDiscoverNoteListByCursor(request.getChannelId(), cursor);
    }

    private DiscoverNotePageResponse<NoteItemRspVO> findDiscoverNoteListByCursor(Long channelId, Long cursor) {
        String version = discoverFeedVersion(channelId);
        DiscoverPageSnapshot snapshot = version == null
                ? loadDiscoverPageSnapshotFromMySql(channelId, cursor)
                : loadDiscoverPageSnapshot(channelId, cursor, version);
        List<NoteItemRspVO> notes = snapshot.getNotes();
        Long nextCursor = snapshot.getNextCursor();
        hydrateVolatileFields(notes);
        return DiscoverNotePageResponse.success(notes, PAGE_SIZE, nextCursor);
    }

    private DiscoverPageSnapshot loadDiscoverPageSnapshot(Long channelId, Long cursor, String version) {
        String cacheKey = RedisKeyConstants.buildDiscoverFeedCursorKey(version, channelId, cursor);
        String lockKey = RedisKeyConstants.buildDiscoverFeedCursorLockKey(version, channelId, cursor);

        DiscoverPageSnapshot snapshot = readDiscoverPageSnapshot(cacheKey);
        if (isValidDiscoverPageSnapshot(snapshot)) {
            return snapshot;
        }

        return CacheRebuildSupport.getOrRebuild(
                new hk.ljx.framework.redisson.lock.RedissonRebuildLock(redissonClient, lockKey, DISCOVER_PAGE_REBUILD_LOCK_SECONDS),
                CACHE_REBUILD_RETRY_TIMES, CACHE_REBUILD_RETRY_INTERVAL_MILLIS,
                () -> {
                    DiscoverPageSnapshot s = readDiscoverPageSnapshot(cacheKey);
                    return isValidDiscoverPageSnapshot(s) ? s : null;
                },
                () -> {
                    DiscoverPageSnapshot fresh = loadDiscoverPageSnapshotFromMySql(channelId, cursor);
                    cacheDiscoverPageSnapshot(cacheKey, fresh);
                    return fresh;
                },
                () -> loadDiscoverPageSnapshotFromMySql(channelId, cursor)
        );
    }

    private DiscoverPageSnapshot loadDiscoverPageSnapshotFromMySql(Long channelId, Long cursor) {
        List<NoteDO> result = noteDOMapper.selectDiscoverPageListByCursor(channelId, cursor, PAGE_SIZE + 1);
        boolean hasMore = result.size() > PAGE_SIZE;
        List<NoteDO> page = CollUtil.sub(result, 0, (int) PAGE_SIZE);
        List<NoteItemRspVO> notes = toNoteItems(page);
        notes.forEach(note -> note.setIsLiked(false));
        Long nextCursor = hasMore && !notes.isEmpty() ? notes.get(notes.size() - 1).getNoteId() : null;
        return new DiscoverPageSnapshot(notes, nextCursor);
    }

    @Override
    public Response<List<FindTopicRspVO>> findTopicList(FindTopicListReqVO request) {
        String keyword = request.getKeyword().trim();
        List<FindTopicRspVO> topics = activeTopics().stream()
                .filter(topic -> StringUtils.containsIgnoreCase(topic.getName(), keyword))
                .limit(20)
                .toList();
        return Response.success(topics);
    }

    private List<NoteItemRspVO> toNoteItems(List<NoteDO> noteDOS) {
        if (CollUtil.isEmpty(noteDOS)) {
            return Collections.emptyList();
        }

        // 计数随快照 JSON 一起缓存，命中路径免 count Feign。
        List<NoteItemRspVO> notes = noteDOS.stream().map(note -> NoteItemRspVO.builder()
                .noteId(note.getId())
                .type(note.getType())
                .cover(getFirstCover(note.getImgUris()))
                .videoUri(note.getVideoUri())
                .title(note.getTitle())
                .creatorId(note.getCreatorId())
                .isLiked(false)
                .build()).collect(Collectors.toList());

        try {
            List<FindUserByIdRspDTO> userList = userClient.findByIds(noteDOS.stream()
                    .map(NoteDO::getCreatorId).distinct().toList());
            if (CollUtil.isNotEmpty(userList)) {
                Map<Long, FindUserByIdRspDTO> users = userList.stream()
                        .collect(Collectors.toMap(FindUserByIdRspDTO::getId, Function.identity(), (left, right) -> left));
                notes.forEach(note -> {
                    FindUserByIdRspDTO user = users.get(note.getCreatorId());
                    if (user != null) {
                        note.setNickname(user.getNickName());
                        note.setAvatar(user.getAvatar());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("RPC 调用用户服务批量获取用户信息失败，发现页列表执行降级", e);
        }
        return notes;
    }

    private static String getFirstCover(String imgUris) {
        if (StringUtils.isBlank(imgUris)) {
            return null;
        }
        int commaIndex = imgUris.indexOf(',');
        return commaIndex >= 0 ? imgUris.substring(0, commaIndex) : imgUris;
    }

    private void hydrateVolatileFields(List<NoteItemRspVO> notes) {
        if (CollUtil.isEmpty(notes)) {
            return;
        }
        // 实时覆盖点赞数（无论快照命中与否，均通过 Redis Pipeline 注入最新点赞数）
        fillCountsIntoNoteItems(notes);
        // 实时覆盖用户红心点赞状态
        setLikedState(notes);
    }


    /** 从 count 服务回填点赞数到快照项（仅重建路径调用；命中路径见 {@link #hydrateVolatileFields}）。 */
    private void fillCountsIntoNoteItems(List<NoteItemRspVO> notes) {
        if (CollUtil.isEmpty(notes)) {
            return;
        }
        Map<Long, FindNoteCountsByIdRspDTO> counts = safeCounts(notes.stream()
                .map(NoteItemRspVO::getNoteId)
                .toList()).stream().collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId,
                Function.identity(), (left, right) -> left));
        notes.forEach(note -> {
            FindNoteCountsByIdRspDTO count = counts.get(note.getNoteId());
            long likeTotal = (count != null && count.getLikeTotal() != null) ? count.getLikeTotal() : 0L;
            note.setLikeTotal(String.valueOf(likeTotal));
        });
    }

    /**
     * 发现页需要按当前登录用户返回点赞状态；不能把列表项固定标记为未点赞。
     */
    private void setLikedState(List<NoteItemRspVO> notes) {
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null || CollUtil.isEmpty(notes)) {
            return;
        }

        List<Long> noteIds = notes.stream().map(NoteItemRspVO::getNoteId).toList();
        Set<Long> likedNoteIds = noteInteractionCacheService.findLikedNoteIds(userId, noteIds);
        notes.forEach(note -> note.setIsLiked(likedNoteIds.contains(note.getNoteId())));
    }

    private List<FindNoteCountsByIdRspDTO> safeCounts(List<Long> noteIds) {
        try {
            List<FindNoteCountsByIdRspDTO> counts = countClient.findByNoteIds(noteIds);
            return counts == null ? Collections.emptyList() : counts;
        } catch (Exception e) {
            log.warn("RPC 调用计数服务批量获取点赞数失败，发现页列表执行降级默认值 0", e);
            return Collections.emptyList();
        }
    }

    private String discoverFeedVersion(Long channelId) {
        // 版本按频道拆分：只影响所属频道与首页(0)。
        String key = RedisKeyConstants.buildDiscoverFeedVersionKey(channelId);
        try {
            Object value = stringRedisTemplate.opsForValue().get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
            String initialVersion = String.valueOf(System.currentTimeMillis());
            stringRedisTemplate.opsForValue().setIfAbsent(key, initialVersion);
            Object current = stringRedisTemplate.opsForValue().get(key);
            return current == null ? initialVersion : String.valueOf(current);
        } catch (Exception e) {
            log.warn("Redis 不可用，发现页跳过缓存并回源 MySQL", e);
            return null;
        }
    }

    private DiscoverPageSnapshot readDiscoverPageSnapshot(String cacheKey) {
        Object cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (!(cached instanceof String cachedJson)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(cachedJson, DiscoverPageSnapshot.class);
        } catch (Exception e) {
            log.warn("发现页缓存解析失败，跳过缓存并尝试删除，key={}", cacheKey, e);
            deleteRedisValue(cacheKey, "发现页缓存");
            return null;
        }
    }

    private void cacheDiscoverPageSnapshot(String cacheKey, DiscoverPageSnapshot snapshot) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonString(snapshot),
                    CacheTtl.basePlusRandom(30, 30), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，发现页缓存写入失败，响应将继续返回，key={}", cacheKey, e);
        }
    }

    private boolean isValidDiscoverPageSnapshot(DiscoverPageSnapshot snapshot) {
        return snapshot != null && snapshot.getNotes() != null;
    }

    private List<FindTopicRspVO> activeTopics() {
        String key = RedisKeyConstants.activeTopicSnapshotKey();
        List<FindTopicRspVO> local = TOPIC_LOCAL_CACHE.getIfPresent(key);
        if (local != null) {
            return local;
        }
        Object cached = getRedisValue(key, "话题快照");
        List<FindTopicRspVO> topics;
        if (cached instanceof String cachedJson) {
            try {
                topics = JsonUtils.parseList(cachedJson, FindTopicRspVO.class);
            } catch (Exception e) {
                log.warn("话题快照缓存解析失败，跳过缓存并尝试删除，key={}", key, e);
                topics = null;
                deleteRedisValue(key, "话题快照");
            }
        } else {
            topics = null;
        }
        if (CollUtil.isEmpty(topics)) {
            topics = topicDOMapper.selectAllEnabled().stream()
                    .map(topic -> FindTopicRspVO.builder().id(topic.getId()).name(topic.getName()).build())
                    .toList();
            cacheTopics(key, topics);
        }
        TOPIC_LOCAL_CACHE.put(key, topics);
        return topics;
    }

    private Object getRedisValue(String key, String cacheName) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，{}读取失败，跳过缓存并回源 MySQL，key={}", cacheName, key, e);
            return null;
        }
    }

    private void cacheChannels(String key, List<FindChannelRspVO> channels) {
        try {
            stringRedisTemplate.opsForValue().set(key, JsonUtils.toJsonString(channels),
                    CacheTtl.basePlusRandom(10, 5), TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 不可用，频道快照缓存写入失败，响应将继续返回，key={}", key, e);
        }
    }

    private void cacheTopics(String key, List<FindTopicRspVO> topics) {
        try {
            stringRedisTemplate.opsForValue().set(key, JsonUtils.toJsonString(topics), 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 不可用，话题快照缓存写入失败，响应将继续返回，key={}", key, e);
        }
    }

    private void deleteRedisValue(String key, String cacheName) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，{}删除失败，key={}", cacheName, key, e);
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class DiscoverPageSnapshot {
        private List<NoteItemRspVO> notes;
        private Long nextCursor;
    }
}
