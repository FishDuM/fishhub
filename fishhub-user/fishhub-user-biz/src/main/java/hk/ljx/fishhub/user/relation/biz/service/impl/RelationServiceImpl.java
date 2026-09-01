package hk.ljx.fishhub.user.relation.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;
import hk.ljx.fishhub.user.biz.domain.mapper.UserDOMapper;
import hk.ljx.fishhub.user.relation.biz.cache.RelationListCacheService;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.biz.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.relation.biz.enums.LuaResultEnum;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.vo.*;
import hk.ljx.fishhub.user.relation.biz.service.RelationService;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.CacheTtl;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.common.util.RedisScriptHelper;
import hk.ljx.framework.mq.support.RocketMqHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private static final DefaultRedisScript<Long> FOLLOW_CHECK_AND_ADD_SCRIPT = RedisScriptHelper.loadLongScript("/lua/follow_check_and_add.lua");
    private static final DefaultRedisScript<Long> UNFOLLOW_CHECK_AND_DELETE_SCRIPT = RedisScriptHelper.loadLongScript("/lua/unfollow_check_and_delete.lua");

    private final StringRedisTemplate stringRedisTemplate;
    private final UserDOMapper userDOMapper;
    private final FollowingDOMapper followingDOMapper;
    private final RocketMQTemplate rocketMQTemplate;
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

        UserDO followUser = userDOMapper.selectActiveById(followUserId);
        if (Objects.isNull(followUser)) {
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
            relationListCacheService.ensureFollowingCache(userId);
            result = stringRedisTemplate.execute(FOLLOW_CHECK_AND_ADD_SCRIPT, Collections.singletonList(followingRedisKey),
                    String.valueOf(followUserId), String.valueOf(timestamp), String.valueOf(expireSeconds));
            checkLuaScriptResult(result);
        }

        if (!Objects.equals(result, LuaResultEnum.FOLLOW_SUCCESS.getCode())) {
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
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
            relationListCacheService.ensureFollowingCache(userId);
            result = stringRedisTemplate.execute(UNFOLLOW_CHECK_AND_DELETE_SCRIPT, Collections.singletonList(followingRedisKey),
                    String.valueOf(unfollowUserId), String.valueOf(expireSeconds));
            if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
                throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
            }
        }

        if (!Objects.equals(result, LuaResultEnum.FOLLOW_SUCCESS.getCode())) {
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
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
     *
     * @param findFollowingListReqVO
     * @return
     */
    @Override
    public RelationCursorPageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO) {
        Long targetUserId = findFollowingListReqVO.getUserId();
        long pageSize = 10L;
        long offset = findFollowingListReqVO.getCursor() == null ? 0L : findFollowingListReqVO.getCursor();

        List<String> memberIds = relationListCacheService.fetchFollowingMembers(targetUserId, offset, (int) pageSize + 1);
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
     *
     * @param request
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
     * 读取用户信息与内聚计数并转换为 VO 粉丝列表（直读本地 Mapper，彻底消除同模块内 Feign RPC 自调用）
     * @param userIds
     * @return
     */
    private List<FindFansUserRspVO> rpcUserServiceAndCountServiceAndDTO2VO(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        List<UserDO> userDOs = userDOMapper.selectByIds(userIds);
        if (CollUtil.isEmpty(userDOs)) {
            return Collections.emptyList();
        }
        Set<Long> followedUserIds = findCurrentUserFollowedIds(userIds);
        return userDOs.stream()
                .map(user -> FindFansUserRspVO.builder()
                        .userId(user.getId())
                        .avatar(user.getAvatar())
                        .nickname(user.getNickname())
                        .noteTotal(user.getNoteCount() != null ? user.getNoteCount().longValue() : 0L)
                        .fansTotal(user.getFansCount() != null ? user.getFansCount().longValue() : 0L)
                        .isFollowed(followedUserIds.contains(user.getId()))
                        .build())
                .toList();
    }

    /**
     * 读取用户信息并转换为 VO 关注列表
     * @param userIds
     * @return
     */
    private List<FindFollowingUserRspVO> rpcUserServiceAndDTO2VO(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        List<UserDO> userDOs = userDOMapper.selectByIds(userIds);
        if (CollUtil.isEmpty(userDOs)) {
            return Collections.emptyList();
        }
        return userDOs.stream()
                .map(user -> FindFollowingUserRspVO.builder()
                        .userId(user.getId())
                        .avatar(user.getAvatar())
                        .nickname(user.getNickname())
                        .introduction(user.getIntroduction())
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
        switch (luaResultEnum) {
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
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
