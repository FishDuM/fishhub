package hk.ljx.fishhub.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
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
import hk.ljx.fishhub.note.biz.rpc.CountRpcService;
import hk.ljx.fishhub.note.biz.rpc.UserRpcService;
import hk.ljx.fishhub.note.biz.service.FeedService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FeedServiceImpl implements FeedService {

    private static final long PAGE_SIZE = 10L;

    private static final int CACHE_REBUILD_RETRY_TIMES = 3;
    private static final long CACHE_REBUILD_RETRY_INTERVAL_MILLIS = 20L;
    private static final long DISCOVER_PAGE_REBUILD_LOCK_SECONDS = 5L;
    private static final String COMPARE_AND_DELETE_LOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private static final Cache<String, List<FindTopicRspVO>> TOPIC_LOCAL_CACHE = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    @Resource
    private ChannelDOMapper channelDOMapper;
    @Resource
    private TopicDOMapper topicDOMapper;
    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private NoteInteractionCacheService noteInteractionCacheService;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private CountRpcService countRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Response<List<FindChannelRspVO>> findChannelList() {
        List<FindChannelRspVO> channels = channelDOMapper.selectAllEnabled().stream()
                .map(channel -> FindChannelRspVO.builder().id(channel.getId()).name(channel.getName()).build())
                .toList();
        return Response.success(channels);
    }

    @Override
    public DiscoverNotePageResponse<NoteItemRspVO> findDiscoverNoteList(FindDiscoverNoteListReqVO request) {
        Long cursor = request.getCursor() > 0 ? request.getCursor() : null;
        return findDiscoverNoteListByCursor(request.getChannelId(), cursor);
    }

    private DiscoverNotePageResponse<NoteItemRspVO> findDiscoverNoteListByCursor(Long channelId, Long cursor) {
        String version = discoverFeedVersion();
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
        DiscoverPageSnapshot snapshot;
        try {
            snapshot = readDiscoverPageSnapshot(cacheKey);
        } catch (Exception e) {
            log.warn("Redis 不可用，发现页缓存读取失败，回源 MySQL，key={}", cacheKey, e);
            return loadDiscoverPageSnapshotFromMySql(channelId, cursor);
        }
        if (isValidDiscoverPageSnapshot(snapshot)) {
            return snapshot;
        }

        String lockToken;
        try {
            lockToken = tryAcquireRebuildLock(lockKey, DISCOVER_PAGE_REBUILD_LOCK_SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，发现页重建锁获取失败，回源 MySQL，key={}", cacheKey, e);
            return loadDiscoverPageSnapshotFromMySql(channelId, cursor);
        }
        if (lockToken == null) {
            try {
                snapshot = waitForDiscoverPageSnapshot(cacheKey);
            } catch (Exception e) {
                log.warn("Redis 不可用，发现页缓存重试失败，回源 MySQL，key={}", cacheKey, e);
                return loadDiscoverPageSnapshotFromMySql(channelId, cursor);
            }
            return snapshot == null ? loadDiscoverPageSnapshotFromMySql(channelId, cursor) : snapshot;
        }
        try {
            try {
                snapshot = readDiscoverPageSnapshot(cacheKey);
            } catch (Exception e) {
                log.warn("Redis 不可用，发现页二次缓存读取失败，回源 MySQL，key={}", cacheKey, e);
                return loadDiscoverPageSnapshotFromMySql(channelId, cursor);
            }
            if (isValidDiscoverPageSnapshot(snapshot)) {
                return snapshot;
            }
            snapshot = loadDiscoverPageSnapshotFromMySql(channelId, cursor);
            cacheDiscoverPageSnapshot(cacheKey, snapshot);
            return snapshot;
        } finally {
            releaseRebuildLock(lockKey, lockToken);
        }
    }

    private DiscoverPageSnapshot loadDiscoverPageSnapshotFromMySql(Long channelId, Long cursor) {
        List<NoteDO> result = noteDOMapper.selectDiscoverPageListByCursor(channelId, cursor, PAGE_SIZE + 1);
        boolean hasMore = result.size() > PAGE_SIZE;
        List<NoteDO> page = hasMore ? result.subList(0, (int) PAGE_SIZE) : result;
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

        List<NoteItemRspVO> notes = noteDOS.stream().map(note -> NoteItemRspVO.builder()
                .noteId(note.getId())
                .type(note.getType())
                .cover(StringUtils.isBlank(note.getImgUris()) ? null : StringUtils.split(note.getImgUris(), ',')[0])
                .videoUri(note.getVideoUri())
                .title(note.getTitle())
                .creatorId(note.getCreatorId())
                .likeTotal("0")
                .isLiked(false)
                .build()).collect(Collectors.toList());

        Map<Long, FindUserByIdRspDTO> users = userRpcService.findByIds(noteDOS.stream()
                        .map(NoteDO::getCreatorId).distinct().toList())
                .stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, Function.identity(), (left, right) -> left));
        notes.forEach(note -> {
            FindUserByIdRspDTO user = users.get(note.getCreatorId());
            if (user != null) {
                note.setNickname(user.getNickName());
                note.setAvatar(user.getAvatar());
            }
        });
        return notes;
    }

    private void hydrateVolatileFields(List<NoteItemRspVO> notes) {
        if (CollUtil.isEmpty(notes)) {
            return;
        }
        Map<Long, FindNoteCountsByIdRspDTO> counts = safeCounts(notes.stream()
                .map(NoteItemRspVO::getNoteId)
                .toList()).stream().collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId,
                Function.identity(), (left, right) -> left));
        notes.forEach(note -> {
            FindNoteCountsByIdRspDTO count = counts.get(note.getNoteId());
            note.setLikeTotal(String.valueOf(count == null || count.getLikeTotal() == null ? 0L : count.getLikeTotal()));
            note.setIsLiked(false);
        });
        setLikedState(notes);
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
        List<FindNoteCountsByIdRspDTO> counts = countRpcService.findByNoteIds(noteIds);
        return counts == null ? Collections.emptyList() : counts;
    }

    private String discoverFeedVersion() {
        String key = RedisKeyConstants.discoverFeedVersionKey();
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
            String initialVersion = String.valueOf(System.currentTimeMillis());
            redisTemplate.opsForValue().setIfAbsent(key, initialVersion);
            Object current = redisTemplate.opsForValue().get(key);
            return current == null ? initialVersion : String.valueOf(current);
        } catch (Exception e) {
            log.warn("Redis 不可用，发现页跳过缓存并回源 MySQL", e);
            return null;
        }
    }

    private DiscoverPageSnapshot readDiscoverPageSnapshot(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
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
            redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonString(snapshot),
                    30 + RandomUtil.randomInt(30), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，发现页缓存写入失败，响应将继续返回，key={}", cacheKey, e);
        }
    }

    private DiscoverPageSnapshot waitForDiscoverPageSnapshot(String cacheKey) {
        for (int i = 0; i < CACHE_REBUILD_RETRY_TIMES; i++) {
            sleepBeforeCacheRetry();
            DiscoverPageSnapshot snapshot = readDiscoverPageSnapshot(cacheKey);
            if (isValidDiscoverPageSnapshot(snapshot)) {
                return snapshot;
            }
        }
        return null;
    }

    private boolean isValidDiscoverPageSnapshot(DiscoverPageSnapshot snapshot) {
        return snapshot != null && snapshot.getNotes() != null;
    }

    private String tryAcquireRebuildLock(String lockKey, long expireSeconds) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    private void releaseRebuildLock(String lockKey, String token) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(COMPARE_AND_DELETE_LOCK_SCRIPT, Long.class);
            redisTemplate.execute(script, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.warn("Redis 不可用，发现页重建锁释放失败，key={}", lockKey, e);
        }
    }

    private void sleepBeforeCacheRetry() {
        try {
            Thread.sleep(CACHE_REBUILD_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，{}读取失败，跳过缓存并回源 MySQL，key={}", cacheName, key, e);
            return null;
        }
    }

    private void cacheTopics(String key, List<FindTopicRspVO> topics) {
        try {
            redisTemplate.opsForValue().set(key, JsonUtils.toJsonString(topics), 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 不可用，话题快照缓存写入失败，响应将继续返回，key={}", key, e);
        }
    }

    private void deleteRedisValue(String key, String cacheName) {
        try {
            redisTemplate.delete(key);
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
