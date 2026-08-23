package hk.ljx.fishhub.user.relation.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.relation.biz.cache.RelationListCacheService;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.enums.LuaResultEnum;
import hk.ljx.fishhub.user.relation.biz.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.vo.*;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.user.relation.biz.service.RelationService;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.framework.common.util.RedisScriptHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import hk.ljx.framework.mq.support.RocketMqHelper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private static final DefaultRedisScript<Long> FOLLOW_CHECK_AND_ADD_SCRIPT = RedisScriptHelper.loadLongScript("/lua/follow_check_and_add.lua");
    private static final DefaultRedisScript<Long> FOLLOW_ADD_AND_EXPIRE_SCRIPT = RedisScriptHelper.loadLongScript("/lua/follow_add_and_expire.lua");
    private static final DefaultRedisScript<Long> FOLLOW_BATCH_ADD_AND_EXPIRE_SCRIPT = RedisScriptHelper.loadLongScript("/lua/follow_batch_add_and_expire.lua");
    private static final DefaultRedisScript<Long> UNFOLLOW_CHECK_AND_DELETE_SCRIPT = RedisScriptHelper.loadLongScript("/lua/unfollow_check_and_delete.lua");

    private final StringRedisTemplate stringRedisTemplate;
    private final UserClient userClient;
    private final FollowingDOMapper followingDOMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final CountClient countClient;
    private final RelationListCacheService relationListCacheService;


    /**
     * 关注用户
     *
     * @param followUserReqVO
     * @return
     */
    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        Long followUserId = followUserReqVO.getFollowUserId();
        Long userId = LoginUserContextHolder.getUserId();

        if (Objects.equals(userId, followUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }

        FindUserByIdRspDTO findUserByIdRspDTO = userClient.findActiveById(followUserId);
        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }

        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        LocalDateTime now = LocalDateTime.now();
        long timestamp = DateUtils.localDateTime2Timestamp(now);
        long expireSeconds = CacheTtl.days(7, 1);

        Long result = stringRedisTemplate.execute(FOLLOW_CHECK_AND_ADD_SCRIPT, Collections.singletonList(followingRedisKey),
                String.valueOf(followUserId), String.valueOf(timestamp), String.valueOf(expireSeconds));

        checkLuaScriptResult(result);

        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);

            if (CollUtil.isEmpty(followingDOS)) {
                stringRedisTemplate.execute(FOLLOW_ADD_AND_EXPIRE_SCRIPT, Collections.singletonList(followingRedisKey),
                        String.valueOf(followUserId), String.valueOf(timestamp), String.valueOf(expireSeconds));
            } else {
                List<FollowingDO> capped = followingDOS.size() > RelationListCacheService.FOLLOWING_LIST_MAX
                        ? followingDOS.subList(0, RelationListCacheService.FOLLOWING_LIST_MAX) : followingDOS;
                String[] luaArgs = buildLuaArgs(capped, expireSeconds);

                stringRedisTemplate.execute(FOLLOW_BATCH_ADD_AND_EXPIRE_SCRIPT, Collections.singletonList(followingRedisKey), (Object[]) luaArgs);

                result = stringRedisTemplate.execute(FOLLOW_CHECK_AND_ADD_SCRIPT, Collections.singletonList(followingRedisKey),
                        String.valueOf(followUserId), String.valueOf(timestamp), String.valueOf(expireSeconds));
                checkLuaScriptResult(result);
            }
        }

        FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                .userId(userId)
                .followUserId(followUserId)
                .createTime(now)
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(followUserMqDTO))
                .build();
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_FOLLOW;
        String hashKey = String.valueOf(userId);

        try {
            RocketMqHelper.syncSendOrderly(rocketMQTemplate, destination, message, hashKey, "关注用户");
        } catch (Exception e) {
            stringRedisTemplate.delete(followingRedisKey);
            throw e;
        }

        return Response.success();
    }

    @Override
    public Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO) {
        Long unfollowUserId = unfollowUserReqVO.getUnfollowUserId();
        Long userId = LoginUserContextHolder.getUserId();

        if (Objects.equals(userId, unfollowUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }

        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        LocalDateTime now = LocalDateTime.now();
        long expireSeconds = CacheTtl.days(7, 1);

        Long result = stringRedisTemplate.execute(UNFOLLOW_CHECK_AND_DELETE_SCRIPT, Collections.singletonList(followingRedisKey),
                String.valueOf(unfollowUserId), String.valueOf(expireSeconds));

        if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
            throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
        }

        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);

            if (CollUtil.isEmpty(followingDOS)) {
                throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
            } else {
                List<FollowingDO> capped = followingDOS.size() > RelationListCacheService.FOLLOWING_LIST_MAX
                        ? followingDOS.subList(0, RelationListCacheService.FOLLOWING_LIST_MAX) : followingDOS;
                String[] luaArgs = buildLuaArgs(capped, expireSeconds);

                stringRedisTemplate.execute(FOLLOW_BATCH_ADD_AND_EXPIRE_SCRIPT, Collections.singletonList(followingRedisKey), (Object[]) luaArgs);

                result = stringRedisTemplate.execute(UNFOLLOW_CHECK_AND_DELETE_SCRIPT, Collections.singletonList(followingRedisKey),
                        String.valueOf(unfollowUserId), String.valueOf(expireSeconds));
                if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
                    throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
                }
            }
        }

        UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                .userId(userId)
                .unfollowUserId(unfollowUserId)
                .createTime(now)
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unfollowUserMqDTO))
                .build();
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_UNFOLLOW;
        String hashKey = String.valueOf(userId);

        try {
            RocketMqHelper.syncSendOrderly(rocketMQTemplate, destination, message, hashKey, "取关用户");
        } catch (Exception e) {
            stringRedisTemplate.delete(followingRedisKey);
            throw e;
        }

        return Response.success();
    }

    /**
     * 查询关注列表
     * @return
     */
    @Override
    public RelationCursorPageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO request) {
        long pageSize = 10L;
        long offset = request.getCursor() == null ? 0L : request.getCursor();
        List<String> memberIds = relationListCacheService.fetchFollowingMembers(request.getUserId(), offset, (int) pageSize + 1);
        if (memberIds.isEmpty()) {
            return RelationCursorPageResponse.success(Collections.emptyList(), pageSize, null);
        }
        boolean hasMore = memberIds.size() > pageSize;
        List<String> pageMembers = hasMore ? memberIds.subList(0, (int) pageSize) : memberIds;
        List<Long> userIds = pageMembers.stream().map(Long::valueOf).toList();
        List<FindFollowingUserRspVO> users = rpcUserServiceAndDTO2VO(userIds);
        Long nextCursor = hasMore ? offset + pageSize : null;
        return RelationCursorPageResponse.success(users, pageSize, nextCursor);
    }

    /**
     * 查询粉丝列表
     * @return
     */
    @Override
    public RelationCursorPageResponse<FindFansUserRspVO> findFansList(FindFansListReqVO request) {
        long pageSize = 10L;
        long offset = request.getCursor() == null ? 0L : request.getCursor();
        List<String> memberIds = relationListCacheService.fetchFansMembers(request.getUserId(), offset, (int) pageSize + 1);
        if (memberIds.isEmpty()) {
            return RelationCursorPageResponse.success(Collections.emptyList(), pageSize, null);
        }
        boolean hasMore = memberIds.size() > pageSize;
        List<String> pageMembers = hasMore ? memberIds.subList(0, (int) pageSize) : memberIds;
        List<Long> userIds = pageMembers.stream().map(Long::valueOf).toList();
        List<FindFansUserRspVO> users = rpcUserServiceAndCountServiceAndDTO2VO(userIds);
        Long nextCursor = hasMore ? offset + pageSize : null;
        return RelationCursorPageResponse.success(users, pageSize, nextCursor);
    }

    /**
     * RPC: 调用用户服务、计数服务，并将 DTO 转换为 VO 粉丝列表
     * @param userIds
     * @return
     */
    private List<FindFansUserRspVO> rpcUserServiceAndCountServiceAndDTO2VO(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        // RPC: 批量查询用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userClient.findByIds(userIds);
        if (CollUtil.isEmpty(findUserByIdRspDTOS)) {
            return Collections.emptyList();
        }
        List<FindUserCountsByIdRspDTO> counts = countClient.findByUserIds(userIds);
        Map<Long, FindUserCountsByIdRspDTO> countMap = CollUtil.isEmpty(counts) ? Collections.emptyMap()
                : counts.stream().collect(Collectors.toMap(FindUserCountsByIdRspDTO::getUserId, Function.identity(), (a, b) -> a));

        Set<Long> followedUserIds = findCurrentUserFollowedIds(userIds);
        return findUserByIdRspDTOS.stream()
                .map(dto -> {
                    FindUserCountsByIdRspDTO count = countMap.get(dto.getId());
                    long noteTotal = (count != null && count.getNoteTotal() != null) ? count.getNoteTotal() : 0L;
                    long fansTotal = (count != null && count.getFansTotal() != null) ? count.getFansTotal() : 0L;
                    return FindFansUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .noteTotal(noteTotal)
                            .fansTotal(fansTotal)
                            .isFollowed(followedUserIds.contains(dto.getId()))
                            .build();
                })
                .toList();
    }

    /**
     * RPC: 调用用户服务，并将 DTO 转换为 VO 关注列表
     * @param userIds
     * @return
     */
    private List<FindFollowingUserRspVO> rpcUserServiceAndDTO2VO(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        // RPC: 批量查询用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userClient.findByIds(userIds);

        if (CollUtil.isEmpty(findUserByIdRspDTOS)) {
            return Collections.emptyList();
        }
        return findUserByIdRspDTOS.stream()
                .map(dto -> FindFollowingUserRspVO.builder()
                        .userId(dto.getId())
                        .avatar(dto.getAvatar())
                        .nickname(dto.getNickName())
                        .introduction(dto.getIntroduction())
                        .isFollowed(true)
                        .build())
                .toList();
    }

    private Set<Long> findCurrentUserFollowedIds(List<Long> candidateUserIds) {
        Long currentUserId = LoginUserContextHolder.getUserId();
        return relationListCacheService.findFollowedUserIds(currentUserId, candidateUserIds);
    }


    /**
     * 校验 Lua 脚本结果，根据状态码抛出对应的业务异常
     * @param result
     */
    private static void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

        if (Objects.isNull(luaResultEnum)) {
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
        }
        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
    }

    /**
     * 构建 Lua 脚本参数
     *
     * @param followingDOS
     * @param expireSeconds
     * @return
     */
    private static String[] buildLuaArgs(List<FollowingDO> followingDOS, long expireSeconds) {
        return RelationListCacheService.buildMemberArgs(followingDOS, FollowingDO::getFollowingUserId, expireSeconds);
    }

    @Override
    public Response<Boolean> isFollowing(CheckFollowingReqVO checkFollowingReqVO) {
        Long userId = LoginUserContextHolder.getUserId();
        Long targetUserId = checkFollowingReqVO.getTargetUserId();
        if (Objects.equals(userId, targetUserId)) return Response.success(false);
        Set<Long> followed = relationListCacheService.findFollowedUserIds(userId, Collections.singletonList(targetUserId));
        return Response.success(CollUtil.isNotEmpty(followed));
    }

    @Override
    public Response<List<Long>> findFollowingIds(CheckFollowingBatchReqVO checkFollowingBatchReqVO) {
        Long userId = LoginUserContextHolder.getUserId();
        List<Long> targetUserIds = checkFollowingBatchReqVO.getTargetUserIds().stream()
                .filter(Objects::nonNull)
                .filter(targetUserId -> !Objects.equals(userId, targetUserId))
                .distinct()
                .toList();
        if (targetUserIds.isEmpty()) {
            return Response.success(Collections.emptyList());
        }
        Set<Long> followed = relationListCacheService.findFollowedUserIds(userId, targetUserIds);
        return Response.success(new ArrayList<>(followed));
    }
}
