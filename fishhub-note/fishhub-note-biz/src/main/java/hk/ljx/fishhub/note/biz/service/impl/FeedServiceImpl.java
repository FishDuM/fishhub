

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

    private static final long PAGE_SIZE = 20L;



    private static final Cache<String, List<FindTopicRspVO>> TOPIC_LOCAL_CACHE = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    // 频道列表本地缓存 1min，避免热路径每请求查一次 MySQL。
    private final Cache<String, List<FindChannelRspVO>> channelLocalCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    // 发现页快照本地短缓存 3s，避免高并发 Feed 流下频繁反序列化 Redis 与锁排队
    private final Cache<String, DiscoverPageSnapshot> feedPageLocalCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(3, TimeUnit.SECONDS)
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
        DiscoverPageSnapshot snapshot = loadDiscoverPageSnapshot(channelId, cursor);
        List<NoteItemRspVO> notes = snapshot == null ? Collections.emptyList() : snapshot.getNotes();
        Long nextCursor = snapshot == null ? null : snapshot.getNextCursor();

        // 仅处理登录用户的动态个性化点赞标记（零 Feign RPC 调用，纯内存/缓存秒回）
        Long userId = LoginUserContextHolder.getUserId();
        if (userId != null && CollUtil.isNotEmpty(notes)) {
            notes = personalizeLikedState(notes, userId);
        }
        return DiscoverNotePageResponse.success(notes, PAGE_SIZE, nextCursor);
    }

    private DiscoverPageSnapshot loadDiscoverPageSnapshot(Long channelId, Long cursor) {
        String cacheKey = RedisKeyConstants.buildDiscoverFeedCursorKey(channelId, cursor);
        DiscoverPageSnapshot localSnap = feedPageLocalCache.getIfPresent(cacheKey);
        if (isValidDiscoverPageSnapshot(localSnap)) {
            return localSnap;
        }

        DiscoverPageSnapshot snapshot = readDiscoverPageSnapshot(cacheKey);
        if (isValidDiscoverPageSnapshot(snapshot)) {
            feedPageLocalCache.put(cacheKey, snapshot);
            return snapshot;
        }

        String lockKey = RedisKeyConstants.buildDiscoverFeedCursorLockKey(channelId, cursor);
        RLock lock = redissonClient.getLock(lockKey);
        
        // 自旋重试机制：最多重试 3 次，消除 50ms 临界点击穿裸打 MySQL 隐患
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            boolean acquired = false;
            try {
                acquired = lock.tryLock(20, 3000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("获取 Feed 游标快照重建锁异常, lockKey={}", lockKey, e);
            }

            if (acquired) {
                try {
                    snapshot = readDiscoverPageSnapshot(cacheKey);
                    if (isValidDiscoverPageSnapshot(snapshot)) {
                        feedPageLocalCache.put(cacheKey, snapshot);
                        return snapshot;
                    }
                    DiscoverPageSnapshot fresh = loadDiscoverPageSnapshotFromMySql(channelId, cursor);
                    cacheDiscoverPageSnapshot(cacheKey, fresh);
                    feedPageLocalCache.put(cacheKey, fresh);
                    return fresh;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            // 未获取到锁：微等待并重试读取 Redis 缓存（等待持锁线程回填完成）
            try {
                Thread.sleep(20L + (long) (Math.random() * 20));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            snapshot = readDiscoverPageSnapshot(cacheKey);
            if (isValidDiscoverPageSnapshot(snapshot)) {
                feedPageLocalCache.put(cacheKey, snapshot);
                return snapshot;
            }
        }

        snapshot = readDiscoverPageSnapshot(cacheKey);
        DiscoverPageSnapshot result = isValidDiscoverPageSnapshot(snapshot) ? snapshot : loadDiscoverPageSnapshotFromMySql(channelId, cursor);
        if (isValidDiscoverPageSnapshot(result)) {
            feedPageLocalCache.put(cacheKey, result);
        }
        return result;
    }

    private DiscoverPageSnapshot loadDiscoverPageSnapshotFromMySql(Long channelId, Long cursor) {
        List<NoteDO> result = noteDOMapper.selectDiscoverPageListByCursor(channelId, cursor, PAGE_SIZE + 1);
        boolean hasMore = result.size() > PAGE_SIZE;
        List<NoteDO> page = CollUtil.sub(result, 0, (int) PAGE_SIZE);
        List<NoteItemRspVO> notes = toNoteItems(page);
        // 一次性烘焙点赞数进快照（仅在快照重建时调用 1 次 Feign，命中路径 0 次 Feign）
        fillCountsIntoNoteItems(notes);
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
                .likeTotal("0")
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

    /** 仅在快照构建时从 count 服务回填点赞数到快照项；命中路径零 Feign RPC */
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
     * 仅按当前登录用户个性化点赞状态（纯本地/Redis Hash匹配，零 Feign RPC）
     */
    private List<NoteItemRspVO> personalizeLikedState(List<NoteItemRspVO> notes, Long userId) {
        try {
            List<Long> noteIds = notes.stream().map(NoteItemRspVO::getNoteId).toList();
            Set<Long> likedNoteIds = noteInteractionCacheService.findLikedNoteIds(userId, noteIds);
            if (CollUtil.isEmpty(likedNoteIds)) {
                return notes;
            }
            return notes.stream().map(note -> {
                boolean isLiked = likedNoteIds.contains(note.getNoteId());
                if (isLiked == Boolean.TRUE.equals(note.getIsLiked())) {
                    return note;
                }
                return note.toBuilder().isLiked(isLiked).build();
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("个性化用户点赞态失败, userId={}", userId, e);
            return notes;
        }
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
