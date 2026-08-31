package hk.ljx.fishhub.count.client;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdsReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdsReqDTO;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 计数服务 RPC 客户端（内置 Caffeine 本地短缓存与精准失效机制）
 */
@RequiredArgsConstructor
public class CountClient {

    private final CountFeignApi countFeignApi;

    /**
     * 笔记计数本地短缓存（3 秒过期，极大降低首页 Feed 流与批量查计数时的 RPC 压力）
     */
    private static final Cache<Long, FindNoteCountsByIdRspDTO> NOTE_COUNT_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1000)
            .maximumSize(10000)
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .build();

    /**
     * 用户计数本地短缓存（3 秒过期）
     */
    private static final Cache<Long, FindUserCountsByIdRspDTO> USER_COUNT_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1000)
            .maximumSize(5000)
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .build();

    /**
     * 主动失效指定笔记的计数缓存（供用户点赞/取消点赞/收藏/评论写接口调用，实现刷新强一致）
     */
    public static void invalidate(Long noteId) {
        if (noteId != null) {
            NOTE_COUNT_LOCAL_CACHE.invalidate(noteId);
        }
    }

    /**
     * 清空全部笔记计数本地缓存
     */
    public static void invalidateAllNotes() {
        NOTE_COUNT_LOCAL_CACHE.invalidateAll();
    }

    /**
     * 主动失效指定用户的计数缓存
     */
    public static void invalidateUser(Long userId) {
        if (userId != null) {
            USER_COUNT_LOCAL_CACHE.invalidate(userId);
        }
    }

    /**
     * 清空全部用户计数本地缓存
     */
    public static void invalidateAllUsers() {
        USER_COUNT_LOCAL_CACHE.invalidateAll();
    }

    /**
     * 批量查询笔记计数（优先命中本地内存，未命中部分批量聚合走 RPC 回源）
     */
    public List<FindNoteCountsByIdRspDTO> findByNoteIds(List<Long> noteIds) {
        if (CollUtil.isEmpty(noteIds)) {
            return Collections.emptyList();
        }

        List<Long> nonNullIds = noteIds.stream().filter(Objects::nonNull).distinct().toList();
        if (CollUtil.isEmpty(nonNullIds)) {
            return Collections.emptyList();
        }

        Map<Long, FindNoteCountsByIdRspDTO> hitMap = new HashMap<>(NOTE_COUNT_LOCAL_CACHE.getAllPresent(nonNullIds));
        List<Long> missedNoteIds = nonNullIds.stream().filter(id -> !hitMap.containsKey(id)).toList();

        if (CollUtil.isNotEmpty(missedNoteIds)) {
            FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO = new FindNoteCountsByIdsReqDTO();
            findNoteCountsByIdsReqDTO.setNoteIds(missedNoteIds);

            Response<List<FindNoteCountsByIdRspDTO>> response = countFeignApi.findNotesCount(findNoteCountsByIdsReqDTO);
            if (response != null && response.isSuccess() && CollUtil.isNotEmpty(response.getData())) {
                for (FindNoteCountsByIdRspDTO count : response.getData()) {
                    if (count != null && count.getNoteId() != null) {
                        NOTE_COUNT_LOCAL_CACHE.put(count.getNoteId(), count);
                        hitMap.put(count.getNoteId(), count);
                    }
                }
            }
        }

        return noteIds.stream()
                .filter(Objects::nonNull)
                .map(hitMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 查询用户计数信息（优先命中本地缓存）
     */
    public FindUserCountsByIdRspDTO findUserCountById(Long userId) {
        if (userId == null) {
            return null;
        }
        FindUserCountsByIdRspDTO cached = USER_COUNT_LOCAL_CACHE.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        FindUserCountsByIdReqDTO findUserCountsByIdReqDTO = new FindUserCountsByIdReqDTO();
        findUserCountsByIdReqDTO.setUserId(userId);

        Response<FindUserCountsByIdRspDTO> response = countFeignApi.findUserCount(findUserCountsByIdReqDTO);
        if (Objects.isNull(response) || !response.isSuccess() || response.getData() == null) {
            return null;
        }

        FindUserCountsByIdRspDTO data = response.getData();
        USER_COUNT_LOCAL_CACHE.put(userId, data);
        return data;
    }

    /**
     * 批量查询用户计数（优先命中本地缓存）
     */
    public List<FindUserCountsByIdRspDTO> findByUserIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }

        List<Long> nonNullIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (CollUtil.isEmpty(nonNullIds)) {
            return Collections.emptyList();
        }

        Map<Long, FindUserCountsByIdRspDTO> hitMap = new HashMap<>(USER_COUNT_LOCAL_CACHE.getAllPresent(nonNullIds));
        List<Long> missedUserIds = nonNullIds.stream().filter(id -> !hitMap.containsKey(id)).toList();

        if (CollUtil.isNotEmpty(missedUserIds)) {
            Response<List<FindUserCountsByIdRspDTO>> response = countFeignApi.findUsersCount(
                    FindUserCountsByIdsReqDTO.builder().userIds(missedUserIds).build());
            if (response != null && response.isSuccess() && CollUtil.isNotEmpty(response.getData())) {
                for (FindUserCountsByIdRspDTO count : response.getData()) {
                    if (count != null && count.getUserId() != null) {
                        USER_COUNT_LOCAL_CACHE.put(count.getUserId(), count);
                        hitMap.put(count.getUserId(), count);
                    }
                }
            }
        }

        return userIds.stream()
                .filter(Objects::nonNull)
                .map(hitMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
