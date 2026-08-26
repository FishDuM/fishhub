

package hk.ljx.fishhub.note.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;
import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.common.util.SafeRedisUtil;
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
    private final SafeRedisUtil safeRedisUtil;
    private final RedissonClient redissonClient;

    @Override
    public Response<List<FindChannelRspVO>> findChannelList() {
        String key = RedisKeyConstants.activeChannelSnapshotKey();
        List<FindChannelRspVO> local = channelLocalCache.getIfPresent(key);
        if (local != null) {
            return Response.success(local);
        }
        List<FindChannelRspVO> channels = safeRedisUtil.getList(key, FindChannelRspVO.class);
        if (CollUtil.isEmpty(channels)) {
            channels = channelDOMapper.selectAllEnabled().stream()
                    .map(channel -> FindChannelRspVO.builder().id(channel.getId()).name(channel.getName()).build())
                    .toList();
            safeRedisUtil.setObject(key, channels, CacheTtl.basePlusRandom(10, 5), TimeUnit.MINUTES);
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
        DiscoverPageSnapshot snapshot = readDiscoverPageSnapshot(cacheKey);
        if (isValidDiscoverPageSnapshot(snapshot)) {
            return snapshot;
        }

        String lockKey = RedisKeyConstants.buildDiscoverFeedCursorLockKey(version, channelId, cursor);
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("获取 Feed 游标快照重建锁异常, lockKey={}", lockKey, e);
        }

        if (acquired) {
            try {
                snapshot = readDiscoverPageSnapshot(cacheKey);
                if (isValidDiscoverPageSnapshot(snapshot)) {
                    return snapshot;
                }
                DiscoverPageSnapshot fresh = loadDiscoverPageSnapshotFromMySql(channelId, cursor);
                cacheDiscoverPageSnapshot(cacheKey, fresh);
                return fresh;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        snapshot = readDiscoverPageSnapshot(cacheKey);
        return isValidDiscoverPageSnapshot(snapshot) ? snapshot : loadDiscoverPageSnapshotFromMySql(channelId, cursor);
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
        String value = safeRedisUtil.get(key);
        if (StringUtils.isNotBlank(value)) {
            return value;
        }
        String initialVersion = String.valueOf(System.currentTimeMillis());
        safeRedisUtil.setIfAbsent(key, initialVersion);
        String current = safeRedisUtil.get(key);
        return current == null ? initialVersion : current;
    }

    private DiscoverPageSnapshot readDiscoverPageSnapshot(String cacheKey) {
        return safeRedisUtil.getObject(cacheKey, DiscoverPageSnapshot.class);
    }

    private void cacheDiscoverPageSnapshot(String cacheKey, DiscoverPageSnapshot snapshot) {
        safeRedisUtil.setObject(cacheKey, snapshot, CacheTtl.basePlusRandom(30, 30), TimeUnit.SECONDS);
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
        List<FindTopicRspVO> topics = safeRedisUtil.getList(key, FindTopicRspVO.class);
        if (CollUtil.isEmpty(topics)) {
            topics = topicDOMapper.selectAllEnabled().stream()
                    .map(topic -> FindTopicRspVO.builder().id(topic.getId()).name(topic.getName()).build())
                    .toList();
            safeRedisUtil.setObject(key, topics, 10, TimeUnit.MINUTES);
        }
        TOPIC_LOCAL_CACHE.put(key, topics);
        return topics;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class DiscoverPageSnapshot {
        private List<NoteItemRspVO> notes;
        private Long nextCursor;
    }
}
