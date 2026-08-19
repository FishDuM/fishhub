package hk.ljx.fishhub.count.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.count.biz.domain.dataobject.NoteCountDO;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.service.NoteCountService;
import hk.ljx.fishhub.count.biz.util.Counts;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdsReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class NoteCountServiceImpl implements NoteCountService {

    private final NoteCountDOMapper noteCountDOMapper;
    private final StringRedisTemplate stringRedisTemplate;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor fishhubTaskExecutor;

    /**
     * 批量查询笔记计数
     *
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    @Override
    public Response<List<FindNoteCountsByIdRspDTO>> findNotesCountData(FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO) {
        // 需要查询的笔记 ID 集合
        List<Long> noteIds = findNoteCountsByIdsReqDTO.getNoteIds();

        // 1. 先查询 Redis 缓存
        // 构建 Redis Hash Key 集合
        List<String> hashKeys = noteIds.stream()
                .map(CountKeyConstants::buildCountNoteKey)
                .toList();

        // 使用 Pipeline 通道，从 Redis 中批量查询笔记 Hash 计数
        List<Object> countHashes = getCountHashesByPipelineFromRedis(hashKeys);

        // 返参 DTO
        List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = Lists.newArrayList();

        // 用于存储缓存中不存在，需要查数据库的笔记 ID
        List<Long> noteIdsNeedQuery = Lists.newArrayList();

        // 循环入参中需要查询的笔记 ID 集合，构建对应 DTO, 并设置缓存中存在的计数，以及过滤出需要查数据库的笔记 ID
        for (int i = 0; i < noteIds.size(); i++) {
            Long currNoteId = noteIds.get(i);
            List<String> currCountHash = (List<String>) countHashes.get(i);

            // 点赞数、收藏数、评论数
            Long likeTotal = toLong(currCountHash.get(0));
            Long collectTotal = toLong(currCountHash.get(1));
            Long commentTotal = toLong(currCountHash.get(2));

            // Hash 中存在任意一个 Field 为 null, 都需要查询数据库
            if (Objects.isNull(likeTotal) || Objects.isNull(collectTotal) || Objects.isNull(commentTotal)) {
                noteIdsNeedQuery.add(currNoteId);
            }

            // 构建 DTO
            FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO = FindNoteCountsByIdRspDTO.builder()
                    .noteId(currNoteId)
                    .likeTotal(likeTotal)
                    .collectTotal(collectTotal)
                    .commentTotal(commentTotal)
                    .build();

            findNoteCountsByIdRspDTOS.add(findNoteCountsByIdRspDTO);
        }

        // 所有 Hash 计数都存在于 Redis 中，直接返参
        if (CollUtil.isEmpty(noteIdsNeedQuery)) {
            return Response.success(findNoteCountsByIdRspDTOS);
        }

        // 2. 若缓存中无，则查询数据库
        // 从数据库中批量查询过滤出的 noteIdsNeedQuery 笔记 ID
        List<NoteCountDO> noteCountDOS = noteCountDOMapper.selectByNoteIds(noteIdsNeedQuery);

        // DO 转 Map；没有计数行的笔记按 0 处理
        Map<Long, NoteCountDO> noteIdAndDOMap = CollUtil.isEmpty(noteCountDOS)
                ? Map.of()
                : noteCountDOS.stream()
                .collect(Collectors.toMap(NoteCountDO::getNoteId, noteCountDO -> noteCountDO));

        // 填充前保留 null 快照再异步回写，避免填充后写回被整体跳过
        List<FindNoteCountsByIdRspDTO> needWriteBack = findNoteCountsByIdRspDTOS.stream()
                .filter(dto -> hasAnyNullCount(dto))
                .map(dto -> FindNoteCountsByIdRspDTO.builder()
                        .noteId(dto.getNoteId())
                        .likeTotal(dto.getLikeTotal())
                        .collectTotal(dto.getCollectTotal())
                        .commentTotal(dto.getCommentTotal())
                        .build())
                .toList();

        // 用库值或 0 补齐 DTO 中为 null 的计数字段
        fillNullCountsFromDb(findNoteCountsByIdRspDTOS, noteIdAndDOMap);

        if (CollUtil.isNotEmpty(needWriteBack)) {
            asyncSyncNoteHash2Redis(needWriteBack, noteIdAndDOMap);
        }

        return Response.success(findNoteCountsByIdRspDTOS);
    }

    private Long toLong(String value) {
        return value == null ? null : Long.parseLong(value);
    }

    private boolean hasAnyNullCount(FindNoteCountsByIdRspDTO dto) {
        return Objects.isNull(dto.getLikeTotal()) || Objects.isNull(dto.getCollectTotal()) || Objects.isNull(dto.getCommentTotal());
    }

    /**
     * 使用数据库结果补齐响应 DTO 中为 null 的计数字段（无计数行按 0 处理）。
     */
    private void fillNullCountsFromDb(List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS, Map<Long, NoteCountDO> noteIdAndDOMap) {
        for (FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO : findNoteCountsByIdRspDTOS) {
            NoteCountDO noteCountDO = noteIdAndDOMap.get(findNoteCountsByIdRspDTO.getNoteId());

            if (Objects.isNull(findNoteCountsByIdRspDTO.getLikeTotal())) {
                findNoteCountsByIdRspDTO.setLikeTotal(Counts.clamp0(Objects.nonNull(noteCountDO) ? noteCountDO.getLikeTotal() : null));
            }
            if (Objects.isNull(findNoteCountsByIdRspDTO.getCollectTotal())) {
                findNoteCountsByIdRspDTO.setCollectTotal(Counts.clamp0(Objects.nonNull(noteCountDO) ? noteCountDO.getCollectTotal() : null));
            }
            if (Objects.isNull(findNoteCountsByIdRspDTO.getCommentTotal())) {
                findNoteCountsByIdRspDTO.setCommentTotal(Counts.clamp0(Objects.nonNull(noteCountDO) ? noteCountDO.getCommentTotal() : null));
            }
        }
    }

    /**
     * 异步回写缺失计数到 Redis；线程池拒绝时降级为同步回写，保证缓存最终建立。
     */
    private void asyncSyncNoteHash2Redis(List<FindNoteCountsByIdRspDTO> needWriteBack, Map<Long, NoteCountDO> noteIdAndDOMap) {
        try {
            fishhubTaskExecutor.execute(() -> {
                try {
                    syncNoteHash2Redis(needWriteBack, noteIdAndDOMap);
                } catch (Exception e) {
                    log.warn("笔记计数异步回写 Redis 失败，等待下次回源重建", e);
                }
            });
        } catch (Exception e) {
            log.warn("笔记计数异步回写任务提交失败，降级为同步回写", e);
            syncNoteHash2Redis(needWriteBack, noteIdAndDOMap);
        }
    }

    /**
     * 将笔记 Hash 计数同步到 Redis 中
     *
     * @param findNoteCountsByIdRspDTOS
     * @param noteIdAndDOMap
     */
    private void syncNoteHash2Redis(List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS, Map<Long, NoteCountDO> noteIdAndDOMap) {
        // 将笔记计数同步到 Redis 中
        stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                // 循环已构建好的返参 DTO 集合
                for (FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO : findNoteCountsByIdRspDTOS) {
                    Long likeTotal = findNoteCountsByIdRspDTO.getLikeTotal();
                    Long collectTotal = findNoteCountsByIdRspDTO.getCollectTotal();
                    Long commentTotal = findNoteCountsByIdRspDTO.getCommentTotal();

                    // 若当前 DTO 的所有计数都不为空，则无需同步 Hash
                    if (Objects.nonNull(likeTotal) && Objects.nonNull(collectTotal) && Objects.nonNull(commentTotal)) {
                        continue;
                    }

                    // 否则，若有任意一个 Field 计数为空，则需要同步对应的 Field
                    Long noteId = findNoteCountsByIdRspDTO.getNoteId();
                    // 构建 Hash Key
                    String noteCountHashKey = CountKeyConstants.buildCountNoteKey(noteId);

                    // 设置 Field 计数
                    Map<String, Long> countMap = Maps.newHashMap();
                    NoteCountDO noteCountDO = noteIdAndDOMap.get(noteId);

                    if (Objects.isNull(likeTotal)) {
                        countMap.put(CountKeyConstants.FIELD_LIKE_TOTAL,
                                Counts.clamp0(Objects.nonNull(noteCountDO) ? noteCountDO.getLikeTotal() : null));
                    }
                    if (Objects.isNull(collectTotal)) {
                        countMap.put(CountKeyConstants.FIELD_COLLECT_TOTAL,
                                Counts.clamp0(Objects.nonNull(noteCountDO) ? noteCountDO.getCollectTotal() : null));
                    }
                    if (Objects.isNull(commentTotal)) {
                        countMap.put(CountKeyConstants.FIELD_COMMENT_TOTAL,
                                Counts.clamp0(Objects.nonNull(noteCountDO) ? noteCountDO.getCommentTotal() : null));
                    }

                    // 批量添加 Hash 的计数 Field，使用 putIfAbsent 防止覆盖并发产生的增量数据
                    for (Map.Entry<String, Long> entry : countMap.entrySet()) {
                        operations.opsForHash().putIfAbsent(noteCountHashKey, entry.getKey(), String.valueOf(entry.getValue()));
                    }

                    // 设置随机过期时间 (1小时以内)
                    long expireTime = CacheTtl.minutes(30, 30);
                    operations.expire(noteCountHashKey, expireTime, TimeUnit.SECONDS);
                }

                return null;
            }
        });
    }

    /**
     * 从 Redis 中批量查询笔记 Hash 计数
     *
     * @param hashKeys
     * @return
     */
    private List<Object> getCountHashesByPipelineFromRedis(List<String> hashKeys) {
        return stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (String hashKey : hashKeys) {
                    // 批量获取多个字段
                    operations.opsForHash().multiGet(hashKey, List.of(
                            CountKeyConstants.FIELD_LIKE_TOTAL,
                            CountKeyConstants.FIELD_COLLECT_TOTAL,
                            CountKeyConstants.FIELD_COMMENT_TOTAL
                    ));
                }
                return null;
            }
        });
    }

}
