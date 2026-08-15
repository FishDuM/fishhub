package hk.ljx.fishhub.user.relation.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FansDO;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FansDOMapper;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.enums.LuaResultEnum;
import hk.ljx.fishhub.user.relation.biz.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.vo.*;
import hk.ljx.fishhub.user.relation.biz.rpc.UserRpcService;
import hk.ljx.fishhub.user.relation.biz.rpc.CountRpcService;
import hk.ljx.fishhub.user.relation.biz.service.RelationService;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;


@Service
@Slf4j
public class RelationServiceImpl implements RelationService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private FansDOMapper fansDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private CountRpcService countRpcService;


    /**
     * 关注用户
     *
     * @param followUserReqVO
     * @return
     */
    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        // 关注的用户 ID
        Long followUserId = followUserReqVO.getFollowUserId();

        Long userId = LoginUserContextHolder.getUserId();

        // 校验：无法关注自己
        if (Objects.equals(userId, followUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }

        // 校验关注的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(followUserId);

        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }

        // 构建当前用户关注列表的 Redis Key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua")));
        script.setResultType(Long.class);

        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // 当前时间转时间戳
        long timestamp = DateUtils.localDateTime2Timestamp(now);

        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);

        // 校验 Lua 脚本执行结果
        checkLuaScriptResult(result);

        // ZSET 不存在
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
            // 从数据库查询当前用户的关注关系记录
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);

            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            // 若记录为空，直接 ZADD 对象, 并设置过期时间
            if (CollUtil.isEmpty(followingDOS)) {
                DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_add_and_expire.lua")));
                script2.setResultType(Long.class);

                redisTemplate.execute(script2, Collections.singletonList(followingRedisKey), followUserId, timestamp, expireSeconds);
            } else { // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

                // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);
                redisTemplate.execute(script3, Collections.singletonList(followingRedisKey), luaArgs);

                // 再次调用上面的 Lua 脚本：follow_check_and_add.lua , 将最新的关注关系添加进去
                result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);
                checkLuaScriptResult(result);
            }
        }

        // 发送 MQ
        FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                .userId(userId)
                .followUserId(followUserId)
                .createTime(now)
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(followUserMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_FOLLOW;

        log.info("==> 开始发送关注操作 MQ, 消息体: {}", followUserMqDTO);

        // 分区键
        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            // Redis 是加速层；发送失败时清空本次关系缓存，后续从 MySQL 重新加载。
            redisTemplate.delete(followingRedisKey);
            throw new IllegalStateException("关注消息发送失败", e);
        }

        return Response.success();
    }

    /**
     * 取关用户
     *
     * @param unfollowUserReqVO
     * @return
     */
    @Override
    public Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO) {
        // 想要取关了用户 ID
        Long unfollowUserId = unfollowUserReqVO.getUnfollowUserId();
        Long userId = LoginUserContextHolder.getUserId();

        // 无法取关自己
        if (Objects.equals(userId, unfollowUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }

        // 校验关注的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(unfollowUserId);

        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }

        // 当前用户的关注列表 Redis Key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/unfollow_check_and_delete.lua")));
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), unfollowUserId);

        // 校验 Lua 脚本执行结果
        // 取关的用户不在关注列表中
        if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
            throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
        }

        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) { // ZSET 关注列表不存在
            // 从数据库查询当前用户的关注关系记录
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);

            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            // 若记录为空，则表示还未关注任何人，提示还未关注对方
            if (CollUtil.isEmpty(followingDOS)) {
                throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
            } else { // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

                // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);
                redisTemplate.execute(script3, Collections.singletonList(followingRedisKey), luaArgs);

                // 再次调用上面的 Lua 脚本：unfollow_check_and_delete.lua , 将取关的用户删除
                result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), unfollowUserId);
                // 再次校验结果
                if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
                    throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
                }
            }
        }

        // 发送 MQ
        UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                .userId(userId)
                .unfollowUserId(unfollowUserId)
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unfollowUserMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_UNFOLLOW;

        log.info("==> 开始发送取关操作 MQ, 消息体: {}", unfollowUserMqDTO);

        String hashKey = String.valueOf(userId);

        try {
            rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        } catch (Exception e) {
            redisTemplate.delete(followingRedisKey);
            throw new IllegalStateException("取关消息发送失败", e);
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
    public RelationCursorPageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO request) {
        long pageSize = 10L;
        List<FollowingDO> records = followingDOMapper.selectCursorPageByUserId(
                request.getUserId(), request.getCursor(), pageSize + 1);
        if (records.isEmpty()) {
            return RelationCursorPageResponse.success(Collections.emptyList(), pageSize, null);
        }
        boolean hasMore = records.size() > pageSize;
        List<FollowingDO> pageRecords = hasMore ? records.subList(0, (int) pageSize) : records;
        List<Long> userIds = pageRecords.stream().map(FollowingDO::getFollowingUserId).toList();
        List<FindFollowingUserRspVO> users = rpcUserServiceAndDTO2VO(userIds, Collections.emptyList());
        Long nextCursor = hasMore ? pageRecords.get(pageRecords.size() - 1).getId() : null;
        return RelationCursorPageResponse.success(users, pageSize, nextCursor);
    }

    /**
     * 查询关注列表
     *
     * @param findFansListReqVO
     * @return
     */
    @Override
    public RelationCursorPageResponse<FindFansUserRspVO> findFansList(FindFansListReqVO request) {
        long pageSize = 10L;
        List<FansDO> records = fansDOMapper.selectCursorPageByUserId(
                request.getUserId(), request.getCursor(), pageSize + 1);
        if (records.isEmpty()) {
            return RelationCursorPageResponse.success(Collections.emptyList(), pageSize, null);
        }
        boolean hasMore = records.size() > pageSize;
        List<FansDO> pageRecords = hasMore ? records.subList(0, (int) pageSize) : records;
        List<Long> userIds = pageRecords.stream().map(FansDO::getFansUserId).toList();
        List<FindFansUserRspVO> users = rpcUserServiceAndCountServiceAndDTO2VO(userIds, Collections.emptyList());
        Long nextCursor = hasMore ? pageRecords.get(pageRecords.size() - 1).getId() : null;
        return RelationCursorPageResponse.success(users, pageSize, nextCursor);
    }

    /**
     * RPC: 调用用户服务、计数服务，并将 DTO 转换为 VO 粉丝列表
     * @param userIds
     * @param findFansUserRspVOS
     * @return
     */
    private List<FindFansUserRspVO> rpcUserServiceAndCountServiceAndDTO2VO(List<Long> userIds, List<FindFansUserRspVO> findFansUserRspVOS) {
        // RPC: 批量查询用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);
        List<FindUserCountsByIdRspDTO> counts = countRpcService.findByUserIds(userIds);
        Map<Long, FindUserCountsByIdRspDTO> countMap = new HashMap<>();
        for (FindUserCountsByIdRspDTO count : counts) {
            countMap.put(count.getUserId(), count);
        }

        Set<Long> followedUserIds = findCurrentUserFollowedIds(userIds);
        // 若不为空，DTO 转 VO
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            findFansUserRspVOS = findUserByIdRspDTOS.stream()
                    .map(dto -> FindFansUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .noteTotal(Optional.ofNullable(countMap.get(dto.getId()))
                                    .map(FindUserCountsByIdRspDTO::getNoteTotal).orElse(0L))
                            .fansTotal(Optional.ofNullable(countMap.get(dto.getId()))
                                    .map(FindUserCountsByIdRspDTO::getFansTotal).orElse(0L))
                            .isFollowed(followedUserIds.contains(dto.getId()))
                            .build())
                    .toList();
        }
        return findFansUserRspVOS;
    }

    /**
     * RPC: 调用用户服务，并将 DTO 转换为 VO 关注列表
     * @param userIds
     * @param findFollowingUserRspVOS
     * @return
     */
    private List<FindFollowingUserRspVO> rpcUserServiceAndDTO2VO(List<Long> userIds, List<FindFollowingUserRspVO> findFollowingUserRspVOS) {
        // RPC: 批量查询用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);

        // 若不为空，DTO 转 VO
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            findFollowingUserRspVOS = findUserByIdRspDTOS.stream()
                    .map(dto -> FindFollowingUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .introduction(dto.getIntroduction())
                            .isFollowed(true)
                            .build())
                    .toList();
        }
        return findFollowingUserRspVOS;
    }

    private Set<Long> findCurrentUserFollowedIds(List<Long> candidateUserIds) {
        Long currentUserId = LoginUserContextHolder.getUserId();
        if (Objects.isNull(currentUserId) || CollUtil.isEmpty(candidateUserIds)) {
            return Collections.emptySet();
        }
        return new HashSet<>(followingDOMapper.selectFollowingUserIds(currentUserId, candidateUserIds));
    }


    /**
     * 校验 Lua 脚本结果，根据状态码抛出对应的业务异常
     * @param result
     */
    private static void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");
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
    private static Object[] buildLuaArgs(List<FollowingDO> followingDOS, long expireSeconds) {
        int argsLength = followingDOS.size() * 2 + 1; // 每个关注关系有 2 个参数（score 和 value），再加一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FollowingDO following : followingDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(following.getCreateTime()); // 关注时间作为 score
            luaArgs[i + 1] = following.getFollowingUserId();          // 关注的用户 ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    @Override
    public Response<Boolean> isFollowing(CheckFollowingReqVO checkFollowingReqVO) {
        Long userId = LoginUserContextHolder.getUserId();
        Long targetUserId = checkFollowingReqVO.getTargetUserId();
        if (Objects.equals(userId, targetUserId)) return Response.success(false);
        List<Long> followed = followingDOMapper.selectFollowingUserIds(userId, Collections.singletonList(targetUserId));
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
        return Response.success(followingDOMapper.selectFollowingUserIds(userId, targetUserIds));
    }
}
