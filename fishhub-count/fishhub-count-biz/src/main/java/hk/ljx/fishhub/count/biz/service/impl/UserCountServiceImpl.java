package hk.ljx.fishhub.count.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.google.common.collect.Maps;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.count.biz.domain.dataobject.UserCountDO;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.service.UserCountService;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import hk.ljx.fishhub.count.biz.util.Counts;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdsReqDTO;
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


@Service
@Slf4j
@RequiredArgsConstructor
public class UserCountServiceImpl implements UserCountService {

    private final UserCountDOMapper userCountDOMapper;
    private final StringRedisTemplate stringRedisTemplate;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final UserCountCacheVersionService userCountCacheVersionService;

    /**
     * 查询用户相关计数
     *
     * @param findUserCountsByIdReqDTO
     * @return
     */
    @Override
    @SentinelResource(value = "findUserCountData", blockHandler = "blockHandler4findUserCountData")
    public Response<FindUserCountsByIdRspDTO> findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO) {
        // 目标用户 ID
        Long userId = findUserCountsByIdReqDTO.getUserId();

        FindUserCountsByIdRspDTO findUserCountByIdRspDTO = FindUserCountsByIdRspDTO.builder()
                .userId(userId)
                .build();

        // 先从 Redis 中查询
        long cacheVersion = userCountCacheVersionService.currentVersion(userId);
        String userCountHashKey = CountKeyConstants.buildCountUserSnapshotKey(userId, cacheVersion);

        List<String> counts = stringRedisTemplate.<String, String>opsForHash()
                .multiGet(userCountHashKey, List.of(
                        CountKeyConstants.FIELD_COLLECT_TOTAL,
                        CountKeyConstants.FIELD_FANS_TOTAL,
                        CountKeyConstants.FIELD_NOTE_TOTAL,
                        CountKeyConstants.FIELD_FOLLOWING_TOTAL,
                        CountKeyConstants.FIELD_LIKE_TOTAL
                ));

        // 若 Hash 中计数不为空，优先以其为主（实时性更高）
        String collectTotal = counts.get(0);
        String fansTotal = counts.get(1);
        String noteTotal = counts.get(2);
        String followingTotal = counts.get(3);
        String likeTotal = counts.get(4);

        findUserCountByIdRspDTO.setCollectTotal(Objects.isNull(collectTotal) ? 0 : Long.parseLong(collectTotal));
        findUserCountByIdRspDTO.setFansTotal(Objects.isNull(fansTotal) ? 0 : Long.parseLong(fansTotal));
        findUserCountByIdRspDTO.setNoteTotal(Objects.isNull(noteTotal) ? 0 : Long.parseLong(noteTotal));
        findUserCountByIdRspDTO.setFollowingTotal(Objects.isNull(followingTotal) ? 0 : Long.parseLong(followingTotal));
        findUserCountByIdRspDTO.setLikeTotal(Objects.isNull(likeTotal) ? 0 : Long.parseLong(likeTotal));

        // 若 Hash 中有任何一个计数为空
        boolean isAnyNull = counts.stream().anyMatch(Objects::isNull);

        if (isAnyNull) {
            // 从数据库查询该用户的计数
            UserCountDO userCountDO = userCountDOMapper.selectByUserId(userId);

            // 判断 Redis 中对应计数，若为空，则使用 DO 中的计数
            if (Objects.nonNull(userCountDO) && Objects.isNull(collectTotal)) {
                findUserCountByIdRspDTO.setCollectTotal(Counts.clamp0(userCountDO.getCollectTotal()));
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(fansTotal)) {
                findUserCountByIdRspDTO.setFansTotal(Counts.clamp0(userCountDO.getFansTotal()));
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(noteTotal)) {
                findUserCountByIdRspDTO.setNoteTotal(Counts.clamp0(userCountDO.getNoteTotal()));
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(followingTotal)) {
                findUserCountByIdRspDTO.setFollowingTotal(Counts.clamp0(userCountDO.getFollowingTotal()));
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(likeTotal)) {
                findUserCountByIdRspDTO.setLikeTotal(Counts.clamp0(userCountDO.getLikeTotal()));
            }

            // 异步同步到 Redis 缓存中, 以便下次查询能够命中缓存
            syncHashCount2Redis(userCountHashKey, userCountDO, collectTotal, fansTotal, noteTotal, followingTotal, likeTotal);
        }

        return Response.success(findUserCountByIdRspDTO);
    }

    @Override
    public Response<List<FindUserCountsByIdRspDTO>> findUsersCountData(FindUserCountsByIdsReqDTO findUserCountsByIdsReqDTO) {
        List<Long> userIds = findUserCountsByIdsReqDTO.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            return Response.success(List.of());
        }

        // 去重
        userIds = userIds.stream().filter(Objects::nonNull).distinct().toList();

        // 1. 查询 Redis
        List<Long> cacheVersions = userCountCacheVersionService.currentVersions(userIds);
        List<String> hashKeys = new java.util.ArrayList<>(userIds.size());
        for (int i = 0; i < userIds.size(); i++) {
            hashKeys.add(CountKeyConstants.buildCountUserSnapshotKey(userIds.get(i), cacheVersions.get(i)));
        }

        List<Object> countHashes = stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (String hashKey : hashKeys) {
                    operations.opsForHash().multiGet(hashKey, List.of(
                            CountKeyConstants.FIELD_COLLECT_TOTAL,
                            CountKeyConstants.FIELD_FANS_TOTAL,
                            CountKeyConstants.FIELD_NOTE_TOTAL,
                            CountKeyConstants.FIELD_FOLLOWING_TOTAL,
                            CountKeyConstants.FIELD_LIKE_TOTAL
                    ));
                }
                return null;
            }
        });

        List<FindUserCountsByIdRspDTO> resultList = new java.util.ArrayList<>();
        List<Long> userIdsNeedQuery = new java.util.ArrayList<>();

        for (int i = 0; i < userIds.size(); i++) {
            Long userId = userIds.get(i);
            List<String> counts = (List<String>) countHashes.get(i);

            String collectTotal = counts.get(0);
            String fansTotal = counts.get(1);
            String noteTotal = counts.get(2);
            String followingTotal = counts.get(3);
            String likeTotal = counts.get(4);

            if (collectTotal == null || fansTotal == null || noteTotal == null || followingTotal == null || likeTotal == null) {
                userIdsNeedQuery.add(userId);
            }

            FindUserCountsByIdRspDTO dto = FindUserCountsByIdRspDTO.builder()
                    .userId(userId)
                    .collectTotal(collectTotal == null ? null : Long.parseLong(collectTotal))
                    .fansTotal(fansTotal == null ? null : Long.parseLong(fansTotal))
                    .noteTotal(noteTotal == null ? null : Long.parseLong(noteTotal))
                    .followingTotal(followingTotal == null ? null : Long.parseLong(followingTotal))
                    .likeTotal(likeTotal == null ? null : Long.parseLong(likeTotal))
                    .build();
            resultList.add(dto);
        }

        // 2. 如果都有缓存，直接返回
        if (userIdsNeedQuery.isEmpty()) {
            return Response.success(resultList);
        }

        // 3. 查数据库兜底
        List<UserCountDO> userCountDOS = userCountDOMapper.selectByUserIds(userIdsNeedQuery);
        Map<Long, UserCountDO> countDOMap = userCountDOS == null || userCountDOS.isEmpty() ? Map.of()
            : userCountDOS.stream().collect(java.util.stream.Collectors.toMap(UserCountDO::getUserId, countDO -> countDO));

        // 4. 回填并异步写缓存
        for (int i = 0; i < resultList.size(); i++) {
            FindUserCountsByIdRspDTO dto = resultList.get(i);
            Long userId = dto.getUserId();
            UserCountDO userCountDO = countDOMap.get(userId);

            @SuppressWarnings("unchecked")
            List<String> rawCounts = (List<String>) (List<?>) countHashes.get(i);
            String rawCollect = rawCounts != null && rawCounts.size() > 0 ? rawCounts.get(0) : null;
            String rawFans = rawCounts != null && rawCounts.size() > 1 ? rawCounts.get(1) : null;
            String rawNote = rawCounts != null && rawCounts.size() > 2 ? rawCounts.get(2) : null;
            String rawFollowing = rawCounts != null && rawCounts.size() > 3 ? rawCounts.get(3) : null;
            String rawLike = rawCounts != null && rawCounts.size() > 4 ? rawCounts.get(4) : null;

            if (dto.getCollectTotal() == null) {
                dto.setCollectTotal(userCountDO == null ? 0L : Counts.clamp0(userCountDO.getCollectTotal()));
            }
            if (dto.getFansTotal() == null) {
                dto.setFansTotal(userCountDO == null ? 0L : Counts.clamp0(userCountDO.getFansTotal()));
            }
            if (dto.getNoteTotal() == null) {
                dto.setNoteTotal(userCountDO == null ? 0L : Counts.clamp0(userCountDO.getNoteTotal()));
            }
            if (dto.getFollowingTotal() == null) {
                dto.setFollowingTotal(userCountDO == null ? 0L : Counts.clamp0(userCountDO.getFollowingTotal()));
            }
            if (dto.getLikeTotal() == null) {
                dto.setLikeTotal(userCountDO == null ? 0L : Counts.clamp0(userCountDO.getLikeTotal()));
            }

            if (userIdsNeedQuery.contains(userId)) {
                int userIndex = userIds.indexOf(userId);
                syncHashCount2Redis(CountKeyConstants.buildCountUserSnapshotKey(userId, cacheVersions.get(userIndex)), userCountDO,
                        rawCollect, rawFans, rawNote, rawFollowing, rawLike);
            }
        }

        return Response.success(resultList);
    }


    /**
     * blockHandler 函数，原方法调用被限流/降级/系统保护的时候调用
     * 注意, 需要包含限流方法的所有参数，和 BlockException 参数
     * @param findUserCountsByIdReqDTO
     * @param blockException
     */
    public Response<FindUserCountsByIdRspDTO> blockHandler4findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO, BlockException blockException) {
        log.warn("## findUserCountData() 方法被限流: {}", JsonUtils.toJsonString(findUserCountsByIdReqDTO));

        return Response.success(FindUserCountsByIdRspDTO.builder()
                        .userId(findUserCountsByIdReqDTO.getUserId())
                        .collectTotal(0L)
                        .fansTotal(0L)
                        .followingTotal(0L)
                        .likeTotal(0L)
                        .noteTotal(0L)
                        .build());
    }

    /**
     * 将该用户的 Hash 计数同步到 Redis 中
     * @param userCountHashKey
     * @param userCountDO
     * @return
     */
    private void syncHashCount2Redis(String userCountHashKey, UserCountDO userCountDO,
                                     String collectTotal, String fansTotal, String noteTotal, String followingTotal, String likeTotal) {
        threadPoolTaskExecutor.submit(() -> {
            try {
                // 存放计数
                Map<String, String> userCountMap = Maps.newHashMap();
                if (Objects.isNull(collectTotal))
                    userCountMap.put(CountKeyConstants.FIELD_COLLECT_TOTAL, String.valueOf(Counts.clamp0(Objects.isNull(userCountDO) ? null : userCountDO.getCollectTotal())));

                if (Objects.isNull(fansTotal))
                    userCountMap.put(CountKeyConstants.FIELD_FANS_TOTAL, String.valueOf(Counts.clamp0(Objects.isNull(userCountDO) ? null : userCountDO.getFansTotal())));

                if (Objects.isNull(noteTotal))
                    userCountMap.put(CountKeyConstants.FIELD_NOTE_TOTAL, String.valueOf(Counts.clamp0(Objects.isNull(userCountDO) ? null : userCountDO.getNoteTotal())));

                if (Objects.isNull(followingTotal))
                    userCountMap.put(CountKeyConstants.FIELD_FOLLOWING_TOTAL, String.valueOf(Counts.clamp0(Objects.isNull(userCountDO) ? null : userCountDO.getFollowingTotal())));

                if (Objects.isNull(likeTotal))
                    userCountMap.put(CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(Counts.clamp0(Objects.isNull(userCountDO) ? null : userCountDO.getLikeTotal())));

                stringRedisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        // 批量添加 Hash 的计数 Field，使用 putIfAbsent 防止覆盖并发产生的增量数据
                        for (Map.Entry<String, String> entry : userCountMap.entrySet()) {
                            operations.opsForHash().putIfAbsent(userCountHashKey, entry.getKey(), entry.getValue());
                        }

                        // 设置随机过期时间 (2小时以内)
                        long expireTime = CacheTtl.hours(1, 1);
                        operations.expire(userCountHashKey, expireTime, TimeUnit.SECONDS);

                        return null;
                    }
                });
            } catch (Exception e) {
                log.warn("Redis 不可用，用户计数 Hash 缓存写入失败", e);
            }
        });
    }
}
