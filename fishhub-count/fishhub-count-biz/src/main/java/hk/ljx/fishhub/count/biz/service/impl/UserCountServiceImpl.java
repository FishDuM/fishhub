package hk.ljx.fishhub.count.biz.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.google.common.collect.Maps;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.dataobject.UserCountDO;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.service.UserCountService;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdsReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class UserCountServiceImpl implements UserCountService {

    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

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
        String userCountHashKey = RedisKeyConstants.buildCountUserKey(userId);

        List<Object> counts = redisTemplate.opsForHash()
                .multiGet(userCountHashKey, List.of(
                        RedisKeyConstants.FIELD_COLLECT_TOTAL,
                        RedisKeyConstants.FIELD_FANS_TOTAL,
                        RedisKeyConstants.FIELD_NOTE_TOTAL,
                        RedisKeyConstants.FIELD_FOLLOWING_TOTAL,
                        RedisKeyConstants.FIELD_LIKE_TOTAL
                ));

        // 若 Hash 中计数不为空，优先以其为主（实时性更高）
        Object collectTotal = counts.get(0);
        Object fansTotal = counts.get(1);
        Object noteTotal = counts.get(2);
        Object followingTotal = counts.get(3);
        Object likeTotal = counts.get(4);

        findUserCountByIdRspDTO.setCollectTotal(Objects.isNull(collectTotal) ? 0 : Long.parseLong(String.valueOf(collectTotal)));
        findUserCountByIdRspDTO.setFansTotal(Objects.isNull(fansTotal) ? 0 : Long.parseLong(String.valueOf(fansTotal)));
        findUserCountByIdRspDTO.setNoteTotal(Objects.isNull(noteTotal) ? 0 : Long.parseLong(String.valueOf(noteTotal)));
        findUserCountByIdRspDTO.setFollowingTotal(Objects.isNull(followingTotal) ? 0 : Long.parseLong(String.valueOf(followingTotal)));
        findUserCountByIdRspDTO.setLikeTotal(Objects.isNull(likeTotal) ? 0 : Long.parseLong(String.valueOf(likeTotal)));

        // 若 Hash 中有任何一个计数为空
        boolean isAnyNull = counts.stream().anyMatch(Objects::isNull);

        if (isAnyNull) {
            // 从数据库查询该用户的计数
            UserCountDO userCountDO = userCountDOMapper.selectByUserId(userId);

            // 判断 Redis 中对应计数，若为空，则使用 DO 中的计数
            if (Objects.nonNull(userCountDO) && Objects.isNull(collectTotal)) {
                findUserCountByIdRspDTO.setCollectTotal(userCountDO.getCollectTotal());
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(fansTotal)) {
                findUserCountByIdRspDTO.setFansTotal(userCountDO.getFansTotal());
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(noteTotal)) {
                findUserCountByIdRspDTO.setNoteTotal(userCountDO.getNoteTotal());
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(followingTotal)) {
                findUserCountByIdRspDTO.setFollowingTotal(userCountDO.getFollowingTotal());
            }
            if (Objects.nonNull(userCountDO) && Objects.isNull(likeTotal)) {
                findUserCountByIdRspDTO.setLikeTotal(userCountDO.getLikeTotal());
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
        List<String> hashKeys = userIds.stream().map(RedisKeyConstants::buildCountUserKey).toList();

        List<Object> countHashes = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (String hashKey : hashKeys) {
                    operations.opsForHash().multiGet(hashKey, List.of(
                            RedisKeyConstants.FIELD_COLLECT_TOTAL,
                            RedisKeyConstants.FIELD_FANS_TOTAL,
                            RedisKeyConstants.FIELD_NOTE_TOTAL,
                            RedisKeyConstants.FIELD_FOLLOWING_TOTAL,
                            RedisKeyConstants.FIELD_LIKE_TOTAL
                    ));
                }
                return null;
            }
        });

        List<FindUserCountsByIdRspDTO> resultList = new java.util.ArrayList<>();
        List<Long> userIdsNeedQuery = new java.util.ArrayList<>();

        for (int i = 0; i < userIds.size(); i++) {
            Long userId = userIds.get(i);
            List<Object> counts = (List<Object>) countHashes.get(i);

            Object collectTotal = counts.get(0);
            Object fansTotal = counts.get(1);
            Object noteTotal = counts.get(2);
            Object followingTotal = counts.get(3);
            Object likeTotal = counts.get(4);

            if (collectTotal == null || fansTotal == null || noteTotal == null || followingTotal == null || likeTotal == null) {
                userIdsNeedQuery.add(userId);
            }

            FindUserCountsByIdRspDTO dto = FindUserCountsByIdRspDTO.builder()
                    .userId(userId)
                    .collectTotal(collectTotal == null ? null : Long.parseLong(String.valueOf(collectTotal)))
                    .fansTotal(fansTotal == null ? null : Long.parseLong(String.valueOf(fansTotal)))
                    .noteTotal(noteTotal == null ? null : Long.parseLong(String.valueOf(noteTotal)))
                    .followingTotal(followingTotal == null ? null : Long.parseLong(String.valueOf(followingTotal)))
                    .likeTotal(likeTotal == null ? null : Long.parseLong(String.valueOf(likeTotal)))
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
        for (FindUserCountsByIdRspDTO dto : resultList) {
            Long userId = dto.getUserId();
            UserCountDO userCountDO = countDOMap.get(userId);

            if (dto.getCollectTotal() == null) {
                dto.setCollectTotal(userCountDO == null || userCountDO.getCollectTotal() == null ? 0L : userCountDO.getCollectTotal());
            }
            if (dto.getFansTotal() == null) {
                dto.setFansTotal(userCountDO == null || userCountDO.getFansTotal() == null ? 0L : userCountDO.getFansTotal());
            }
            if (dto.getNoteTotal() == null) {
                dto.setNoteTotal(userCountDO == null || userCountDO.getNoteTotal() == null ? 0L : userCountDO.getNoteTotal());
            }
            if (dto.getFollowingTotal() == null) {
                dto.setFollowingTotal(userCountDO == null || userCountDO.getFollowingTotal() == null ? 0L : userCountDO.getFollowingTotal());
            }
            if (dto.getLikeTotal() == null) {
                dto.setLikeTotal(userCountDO == null || userCountDO.getLikeTotal() == null ? 0L : userCountDO.getLikeTotal());
            }

            if (userIdsNeedQuery.contains(userId)) {
                syncHashCount2Redis(RedisKeyConstants.buildCountUserKey(userId), userCountDO,
                        null, null, null, null, null);
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
                                     Object collectTotal, Object fansTotal, Object noteTotal, Object followingTotal, Object likeTotal) {
        threadPoolTaskExecutor.submit(() -> {
            // 存放计数
            Map<String, Long> userCountMap = Maps.newHashMap();
            if (Objects.isNull(collectTotal))
                userCountMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL, Objects.isNull(userCountDO) || Objects.isNull(userCountDO.getCollectTotal()) ? 0 : userCountDO.getCollectTotal());

            if (Objects.isNull(fansTotal))
                userCountMap.put(RedisKeyConstants.FIELD_FANS_TOTAL, Objects.isNull(userCountDO) || Objects.isNull(userCountDO.getFansTotal()) ? 0 : userCountDO.getFansTotal());

            if (Objects.isNull(noteTotal))
                userCountMap.put(RedisKeyConstants.FIELD_NOTE_TOTAL, Objects.isNull(userCountDO) || Objects.isNull(userCountDO.getNoteTotal()) ? 0 : userCountDO.getNoteTotal());

            if (Objects.isNull(followingTotal))
                userCountMap.put(RedisKeyConstants.FIELD_FOLLOWING_TOTAL, Objects.isNull(userCountDO) || Objects.isNull(userCountDO.getFollowingTotal()) ? 0 : userCountDO.getFollowingTotal());

            if (Objects.isNull(likeTotal))
                userCountMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL, Objects.isNull(userCountDO) || Objects.isNull(userCountDO.getLikeTotal()) ? 0 : userCountDO.getLikeTotal());

            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    // 批量添加 Hash 的计数 Field
                    operations.opsForHash().putAll(userCountHashKey, userCountMap);

                    // 设置随机过期时间 (2小时以内)
                    long expireTime = 60*60 + RandomUtil.randomInt(60 * 60);
                    operations.expire(userCountHashKey, expireTime, TimeUnit.SECONDS);

                    return null;
                }
            });
        });
    }
}
