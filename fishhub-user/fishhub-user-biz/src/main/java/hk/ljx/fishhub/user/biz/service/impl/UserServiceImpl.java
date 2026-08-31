package hk.ljx.fishhub.user.biz.service.impl;

import hk.ljx.framework.common.util.CacheTtl;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.enums.DeletedEnum;
import hk.ljx.framework.common.enums.StatusEnum;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.common.util.NumberUtils;
import hk.ljx.framework.common.util.ParamUtils;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.user.biz.constant.MQConstants;
import hk.ljx.fishhub.user.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;
import hk.ljx.fishhub.user.biz.domain.mapper.UserDOMapper;
import hk.ljx.fishhub.user.biz.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.enums.SexEnum;
import hk.ljx.fishhub.user.biz.model.vo.FindUserProfileReqVO;
import hk.ljx.fishhub.user.biz.model.vo.FindUserProfileRspVO;
import hk.ljx.fishhub.user.biz.model.vo.UpdateUserInfoReqVO;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.user.biz.rpc.DistributedIdGeneratorRpcService;
import hk.ljx.fishhub.user.biz.rpc.OssRpcService;
import hk.ljx.fishhub.user.biz.service.UserService;
import hk.ljx.fishhub.user.dto.req.*;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.dto.rsp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.rsp.ResolveLoginableUserRspDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import hk.ljx.framework.mq.support.RocketMqHelper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserDOMapper userDOMapper;
    private final OssRpcService ossRpcService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final CountClient countClient;
    private final RocketMQTemplate rocketMQTemplate;

    /** 可关注用户本地缓存：Caffeine.get 自带单飞，只做本节点合并，不做跨节点锁 */
    private final Cache<Long, Optional<FindUserByIdRspDTO>> activeUserLocalCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(2, TimeUnit.SECONDS)
            .build();

    /** 可关注用户 Redis 缓存 TTL：禁用/删除操作的最长生效滞后窗口，必须远短于 user:info(1天) */
    private static final long ACTIVE_CACHE_TTL_SECONDS = 15L;
    /** 防穿透 null 哨兵 TTL：短于正常 TTL，避免不存在的用户长期占位 */
    private static final long ACTIVE_CACHE_NULL_TTL_SECONDS = 3L;

    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO) {
        // 仅允许当前登录用户修改自己的资料，用户 ID 不接受客户端传入。
        Long userId = LoginUserContextHolder.getUserId();

        UserDO userDO = new UserDO();
        userDO.setId(userId);
        boolean needUpdate = false;

        MultipartFile avatarFile = updateUserInfoReqVO.getAvatar();

        if (Objects.nonNull(avatarFile)) {
            String avatar = ossRpcService.uploadFile(avatarFile);
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", avatar);

            if (StringUtils.isBlank(avatar)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            }

            userDO.setAvatar(avatar);
            needUpdate = true;
        }

        String nickname = updateUserInfoReqVO.getNickname();
        if (StringUtils.isNotBlank(nickname)) {
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL.getErrorMessage());
            userDO.setNickname(nickname);
            needUpdate = true;
        }
        String fishhubId = updateUserInfoReqVO.getFishhubId();
        if (StringUtils.isNotBlank(fishhubId)) {
            Preconditions.checkArgument(ParamUtils.checkFishhubId(fishhubId), ResponseCodeEnum.FISHHUB_ID_VALID_FAIL.getErrorMessage());
            userDO.setFishhubId(fishhubId);
            needUpdate = true;
        }

        Integer sex = updateUserInfoReqVO.getSex();
        if (Objects.nonNull(sex)) {
            Preconditions.checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL.getErrorMessage());
            userDO.setSex(sex);
            needUpdate = true;
        }

        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (Objects.nonNull(birthday)) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        String introduction = updateUserInfoReqVO.getIntroduction();
        if (introduction != null) {
            Preconditions.checkArgument(introduction.length() <= 100,
                    ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getErrorMessage());
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        MultipartFile backgroundImgFile = updateUserInfoReqVO.getBackgroundImg();
        if (Objects.nonNull(backgroundImgFile)) {
            String backgroundImg = ossRpcService.uploadFile(backgroundImgFile);
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", backgroundImg);

            if (StringUtils.isBlank(backgroundImg)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            }

            userDO.setBackgroundImg(backgroundImg);
            needUpdate = true;
        }

        if (needUpdate) {
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);

            // 数据落库后立即清理 Redis 缓存，避免读到旧资料。
            deleteUserRedisCache(userId);

            // 延时双删
            sendDelayDeleteUserRedisCacheMQ(userId);

            // 通知搜索服务同步用户与笔记索引（尽力而为，失败下次更新补偿）
            RocketMqHelper.asyncSend(rocketMQTemplate, MQConstants.TOPIC_USER_CHANGED,
                    String.valueOf(userId), "用户资料变更同步 ES");
        }
        return Response.success();
    }

    /**
     * 异步发送延时消息
     */
    private void sendDelayDeleteUserRedisCacheMQ(Long userId) {
        Message<String> message = MessageBuilder.withPayload(String.valueOf(userId)).build();
        RocketMqHelper.asyncSendDelay(rocketMQTemplate, MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE,
                message, 3000, 1, "延时删除 Redis 用户缓存");
    }

    /**
     * 删除 Redis 中的用户缓存
     * @param userId
     */
    private void deleteUserRedisCache(Long userId) {
        String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);
        String userProfileRedisKey = RedisKeyConstants.buildUserProfileKey(userId);
        String userActiveRedisKey = RedisKeyConstants.buildUserActiveKey(userId);

        stringRedisTemplate.delete(Arrays.asList(userInfoRedisKey, userProfileRedisKey, userActiveRedisKey));
    }

    /**
     * 用户注册
     *
     * @param phone
     * @param encodePassword
     * @return
     */
    @Override
    public Response<Long> register(String phone, String encodePassword) {
        UserDO existingUser = userDOMapper.selectByPhone(phone);
        if (Objects.nonNull(existingUser)) {
            throw new BizException(hk.ljx.fishhub.user.biz.auth.enums.ResponseCodeEnum.PHONE_ALREADY_REGISTERED);
        }

        String fishhubId = distributedIdGeneratorRpcService.getFishhubId();
        String userIdStr = distributedIdGeneratorRpcService.getUserId();
        Long userId = Long.valueOf(userIdStr);

        String defaultNickname = "小鱼_" + (userIdStr.length() > 6 ? userIdStr.substring(userIdStr.length() - 6) : userIdStr);

        UserDO newUser = UserDO.builder()
                .id(userId)
                .phone(phone)
                .password(encodePassword)
                .fishhubId(fishhubId)
                .nickname(defaultNickname)
                .status(StatusEnum.ENABLE.getValue())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue())
                .build();

        if (userDOMapper.insertIfAbsent(newUser) == 0) {
            throw new BizException(hk.ljx.fishhub.user.biz.auth.enums.ResponseCodeEnum.PHONE_ALREADY_REGISTERED);
        }

        return Response.success(userId);
    }

    /**
     * 查询手机号对应的可登录账号；不存在时创建默认账号。
     *
     * @param request
     * @return
     */
    @Override
    public Response<ResolveLoginableUserRspDTO> resolveOrRegisterLoginableUser(ResolveLoginableUserReqDTO request) {
        String phone = request.getPhone();

        // 1. 无事务快速无锁查询已有用户（老用户登录直接返回，不霸占 DB 连接与行锁）
        UserDO existingUser = userDOMapper.selectByPhone(phone);
        log.info("手机号查询完成，found={}", existingUser != null);

        if (Objects.nonNull(existingUser)) {
            return resolvedLoginableUserResponse(existingUser);
        }

        // 2. 在事务外部执行远程网络 RPC，避免网络 IO 产生长事务拖垮数据库连接池
        String fishhubId = distributedIdGeneratorRpcService.getFishhubId();
        String userIdStr = distributedIdGeneratorRpcService.getUserId();
        Long userId = Long.valueOf(userIdStr);

        String defaultNickname = "小鱼_" + (userIdStr.length() > 6 ? userIdStr.substring(userIdStr.length() - 6) : userIdStr);

        UserDO newUser = UserDO.builder()
                .id(userId)
                .phone(phone)
                .fishhubId(fishhubId) // 自动生成小鱼号 ID
                .nickname(defaultNickname) // 自动生成昵称, 如：小鱼_123456
                .status(StatusEnum.ENABLE.getValue()) // 状态为启用
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue()) // 逻辑删除
                .build();

        // 3. 并发安全创建用户
        if (userDOMapper.insertIfAbsent(newUser) == 0) {
            // 并发注册冲突时，查询已由另一线程成功创建的账号
            UserDO concurrentUser = userDOMapper.selectByPhone(phone);
            if (concurrentUser == null) {
                throw new IllegalStateException("手机号账号创建后未找到");
            }
            return resolvedLoginableUserResponse(concurrentUser);
        }

        return resolvedLoginableUserResponse(newUser);
    }

    private Response<ResolveLoginableUserRspDTO> resolvedLoginableUserResponse(UserDO user) {
        boolean loginable = Objects.equals(user.getStatus(), StatusEnum.ENABLE.getValue())
                && Objects.equals(user.getIsDeleted(), DeletedEnum.NO.getValue());
        return Response.success(ResolveLoginableUserRspDTO.builder()
                .userId(loginable ? user.getId() : null)
                .loginable(loginable)
                .build());
    }

    /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return
     */
    @Override
    public Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        String phone = findUserByPhoneReqDTO.getPhone();

        UserDO userDO = userDOMapper.selectActiveByPhone(phone);

        if (Objects.isNull(userDO)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserByPhoneRspDTO findUserByPhoneRspDTO = FindUserByPhoneRspDTO.builder()
                .id(userDO.getId())
                .password(userDO.getPassword())
                .build();

        return Response.success(findUserByPhoneRspDTO);
    }

    /**
     * 更新密码
     *
     * @param updateUserPasswordReqDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        Long userId = LoginUserContextHolder.getUserId();

        UserDO userDO = UserDO.builder()
                .id(userId)
                .password(updateUserPasswordReqDTO.getEncodePassword()) // 加密后的密码
                .updateTime(LocalDateTime.now())
                .build();
        int updated = userDOMapper.updateByPrimaryKeySelective(userDO);
        if (updated != 1) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        return Response.success(Boolean.TRUE);
    }

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param findUserByIdReqDTO
     * @return
     */
    @Override
    public Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO) {
        Long userId = findUserByIdReqDTO.getId();

        String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);

        String userInfoRedisValue = stringRedisTemplate.opsForValue().get(userInfoRedisKey);

        if (StringUtils.isNotBlank(userInfoRedisValue)) {
            if ("null".equals(userInfoRedisValue)) {
                throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
            }
            FindUserByIdRspDTO findUserByIdRspDTO = JsonUtils.parseObject(userInfoRedisValue, FindUserByIdRspDTO.class);
            return Response.success(findUserByIdRspDTO);
        }

        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);

        if (Objects.isNull(userDO)) {
            threadPoolTaskExecutor.execute(() -> {
                // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long expireSeconds = CacheTtl.minutes(1, 1);
                stringRedisTemplate.opsForValue().set(userInfoRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserByIdRspDTO findUserByIdRspDTO = toFindUserByIdRspDTO(userDO);

        threadPoolTaskExecutor.submit(() -> {
            try {
                // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
                long expireSeconds = CacheTtl.days(1, 1);
                stringRedisTemplate.opsForValue()
                        .set(userInfoRedisKey, JsonUtils.toJsonString(findUserByIdRspDTO), expireSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis 不可用，用户信息缓存写入失败，响应将继续返回", e);
            }
        });

        return Response.success(findUserByIdRspDTO);
    }

    @Override
    public Response<FindUserByIdRspDTO> findActiveById(FindUserByIdReqDTO findUserByIdReqDTO) {
        // 约定：data=null 表示用户不存在或已禁用/删除，由调用方决定业务语义（关注时转为 FOLLOW_USER_NOT_EXISTED）
        Long userId = findUserByIdReqDTO == null ? null : findUserByIdReqDTO.getId();
        if (userId == null) {
            return Response.success(null);
        }
        return Response.success(activeUserLocalCache.get(userId, this::loadActiveUser).orElse(null));
    }

    private Optional<FindUserByIdRspDTO> loadActiveUser(Long userId) {
        String key = RedisKeyConstants.buildUserActiveKey(userId);
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                if ("null".equals(cached)) {
                    return Optional.empty();
                }
                FindUserByIdRspDTO cachedUser = JsonUtils.parseObject(cached, FindUserByIdRspDTO.class);
                if (cachedUser != null) {
                    return Optional.of(cachedUser);
                }
            }
            // 缓存未命中回源 DB（status=0 且未删除），写回缓存
            UserDO userDO = userDOMapper.selectActiveById(userId);
            if (userDO == null) {
                stringRedisTemplate.opsForValue().set(key, "null", ACTIVE_CACHE_NULL_TTL_SECONDS, TimeUnit.SECONDS);
                return Optional.empty();
            }
            FindUserByIdRspDTO dto = toFindUserByIdRspDTO(userDO);
            stringRedisTemplate.opsForValue().set(key, JsonUtils.toJsonString(dto),
                    ACTIVE_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            return Optional.of(dto);
        } catch (Exception e) {
            // Redis 异常按缓存 miss 处理，直接返回 DB 结果，不阻断业务
            log.warn("可关注用户缓存读写失败，回源 DB, userId={}", userId, e);
            UserDO userDO = userDOMapper.selectActiveById(userId);
            return userDO == null ? Optional.empty() : Optional.of(toFindUserByIdRspDTO(userDO));
        }
    }

    private static FindUserByIdRspDTO toFindUserByIdRspDTO(UserDO userDO) {
        return FindUserByIdRspDTO.builder()
                .id(userDO.getId())
                .nickName(userDO.getNickname())
                .avatar(userDO.getAvatar())
                .introduction(userDO.getIntroduction())
                .build();
    }

    /**
     * 批量根据用户 ID 查询用户信息
     *
     * @param findUsersByIdsReqDTO
     * @return
     */
    @Override
    public Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        List<Long> userIds = findUsersByIdsReqDTO.getIds();
        if (CollUtil.isEmpty(userIds)) {
            return Response.success(Collections.emptyList());
        }

        List<Long> distinctUserIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctUserIds.isEmpty()) {
            return Response.success(Collections.emptyList());
        }

        List<String> redisKeys = distinctUserIds.stream()
                .map(RedisKeyConstants::buildUserInfoKey)
                .toList();

        List<String> redisValues = stringRedisTemplate.opsForValue().multiGet(redisKeys);

        Map<Long, FindUserByIdRspDTO> foundUsersMap = new HashMap<>();
        List<Long> userIdsNeedQuery = new ArrayList<>();

        if (CollUtil.isNotEmpty(redisValues)) {
            for (int i = 0; i < distinctUserIds.size(); i++) {
                Long uid = distinctUserIds.get(i);
                String val = redisValues.get(i);
                if (val == null) {
                    userIdsNeedQuery.add(uid);
                } else if (!"null".equals(val)) {
                    FindUserByIdRspDTO dto = JsonUtils.parseObject(val, FindUserByIdRspDTO.class);
                    if (dto != null) {
                        foundUsersMap.put(uid, dto);
                    } else {
                        userIdsNeedQuery.add(uid);
                    }
                }
                // "null" 哨兵说明该用户确定不存在，防穿透拦截，不加入 userIdsNeedQuery
            }
        } else {
            userIdsNeedQuery.addAll(distinctUserIds);
        }

        // 若全部命中缓存（包括存在的与已确定不存在的哨兵），直接按序组装返回
        if (userIdsNeedQuery.isEmpty()) {
            return Response.success(orderUsersByRequestIds(userIds, new ArrayList<>(foundUsersMap.values())));
        }

        // 从数据库中批量查询缺失的用户
        List<UserDO> userDOS = userDOMapper.selectByIds(userIdsNeedQuery);
        List<FindUserByIdRspDTO> dbFoundRspDTOs = new ArrayList<>();
        Set<Long> dbFoundUserIds = new HashSet<>();

        if (CollUtil.isNotEmpty(userDOS)) {
            for (UserDO userDO : userDOS) {
                FindUserByIdRspDTO dto = toFindUserByIdRspDTO(userDO);
                dbFoundRspDTOs.add(dto);
                dbFoundUserIds.add(userDO.getId());
                foundUsersMap.put(userDO.getId(), dto);
            }
        }

        // 异步写缓存：查到的写正常数据（1天+随机），未查到的写 "null" 哨兵（1分钟+随机）防穿透
        List<Long> nonExistentIds = userIdsNeedQuery.stream()
                .filter(id -> !dbFoundUserIds.contains(id))
                .toList();

        threadPoolTaskExecutor.submit(() -> {
            try {
                stringRedisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        for (FindUserByIdRspDTO dto : dbFoundRspDTOs) {
                            String key = RedisKeyConstants.buildUserInfoKey(dto.getId());
                            long expireSeconds = CacheTtl.days(1, 1);
                            operations.opsForValue().set(key, JsonUtils.toJsonString(dto), expireSeconds, TimeUnit.SECONDS);
                        }
                        for (Long nonExistentId : nonExistentIds) {
                            String key = RedisKeyConstants.buildUserInfoKey(nonExistentId);
                            long expireSeconds = CacheTtl.minutes(1, 1);
                            operations.opsForValue().set(key, "null", expireSeconds, TimeUnit.SECONDS);
                        }
                        return null;
                    }
                });
            } catch (Exception e) {
                log.warn("Redis 不可用，用户信息批量缓存写入失败", e);
            }
        });

        return Response.success(orderUsersByRequestIds(userIds, new ArrayList<>(foundUsersMap.values())));
    }

    /**
     * RPC 的顺序契约：返回顺序与请求 ID 顺序一致；不可见用户自然被省略。
     */
    private List<FindUserByIdRspDTO> orderUsersByRequestIds(List<Long> userIds,
                                                            List<FindUserByIdRspDTO> users) {
        Map<Long, FindUserByIdRspDTO> usersById = users.stream()
                .collect(Collectors.toMap(FindUserByIdRspDTO::getId, user -> user, (left, right) -> left));
        return userIds.stream().map(usersById::get).filter(Objects::nonNull).toList();
    }

    /**
     * 获取用户主页信息
     *
     * @param findUserProfileReqVO
     * @return
     */
    @Override
    public Response<FindUserProfileRspVO> findUserProfile(FindUserProfileReqVO findUserProfileReqVO) {
        Long userId = findUserProfileReqVO.getUserId();

        if (Objects.isNull(userId)) {
            userId = LoginUserContextHolder.getUserId();
        }

        if (Objects.isNull(userId)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        String userProfileRedisKey = RedisKeyConstants.buildUserProfileKey(userId);

        String userProfileJson = stringRedisTemplate.opsForValue().get(userProfileRedisKey);

        if (StringUtils.isNotBlank(userProfileJson)) {
            FindUserProfileRspVO findUserProfileRspVO = JsonUtils.parseObject(userProfileJson, FindUserProfileRspVO.class);
            // 无论是作者本人还是访客查看，均统一覆盖最新的实时动态计数（~0.3ms，直查计数服务 Redis Hash）
            rpcCountServiceAndSetData(userId, findUserProfileRspVO);

            return Response.success(findUserProfileRspVO);
        }

        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);

        if (Objects.isNull(userDO)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserProfileRspVO findUserProfileRspVO = FindUserProfileRspVO.builder()
                .userId(userDO.getId())
                .avatar(userDO.getAvatar())
                .nickname(userDO.getNickname())
                .fishhubId(userDO.getFishhubId())
                .sex(userDO.getSex())
                .birthday(userDO.getBirthday())
                .introduction(userDO.getIntroduction())
                .build();

        LocalDate birthday = userDO.getBirthday();
        findUserProfileRspVO.setAge(Objects.isNull(birthday) ? null : DateUtils.calculateAge(birthday));

        syncUserProfile2Redis(userProfileRedisKey, findUserProfileRspVO);

        // 动态覆盖计数
        rpcCountServiceAndSetData(userId, findUserProfileRspVO);

        return Response.success(findUserProfileRspVO);
    }

    /**
     * Feign 调用计数服务, 并设置计数数据
     * @param userId
     * @param findUserProfileRspVO
     */
    private void rpcCountServiceAndSetData(Long userId, FindUserProfileRspVO findUserProfileRspVO) {
        try {
            FindUserCountsByIdRspDTO findUserCountsByIdRspDTO = countClient.findUserCountById(userId);

            if (Objects.nonNull(findUserCountsByIdRspDTO)) {
                Long fansTotal = findUserCountsByIdRspDTO.getFansTotal();
                Long followingTotal = findUserCountsByIdRspDTO.getFollowingTotal();
                Long likeTotal = findUserCountsByIdRspDTO.getLikeTotal();
                Long collectTotal = findUserCountsByIdRspDTO.getCollectTotal();
                Long noteTotal = findUserCountsByIdRspDTO.getNoteTotal();

                long safeLike = likeTotal == null ? 0L : likeTotal;
                long safeCollect = collectTotal == null ? 0L : collectTotal;

                findUserProfileRspVO.setFansTotal(NumberUtils.formatNumberString(fansTotal == null ? 0L : fansTotal));
                findUserProfileRspVO.setFollowingTotal(NumberUtils.formatNumberString(followingTotal == null ? 0L : followingTotal));
                findUserProfileRspVO.setLikeAndCollectTotal(NumberUtils.formatNumberString(safeLike + safeCollect));
                findUserProfileRspVO.setNoteTotal(NumberUtils.formatNumberString(noteTotal == null ? 0L : noteTotal));
                findUserProfileRspVO.setLikeTotal(NumberUtils.formatNumberString(safeLike));
                findUserProfileRspVO.setCollectTotal(NumberUtils.formatNumberString(safeCollect));
            }
        } catch (Exception e) {
            log.warn("RPC 调用计数服务获取用户计数失败，保留兜底计数, userId={}", userId, e);
        }
    }

    /**
     * 异步同步到 Redis 中
     *
     * @param userProfileRedisKey
     * @param findUserProfileRspVO
     */
    private void syncUserProfile2Redis(String userProfileRedisKey, FindUserProfileRspVO findUserProfileRspVO) {
        threadPoolTaskExecutor.submit(() -> {
            try {
                long expireTime = CacheTtl.hours(1, 1);

                stringRedisTemplate.opsForValue().set(userProfileRedisKey, JsonUtils.toJsonString(findUserProfileRspVO), expireTime, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis 不可用，用户主页缓存写入失败", e);
            }
        });
    }
}
