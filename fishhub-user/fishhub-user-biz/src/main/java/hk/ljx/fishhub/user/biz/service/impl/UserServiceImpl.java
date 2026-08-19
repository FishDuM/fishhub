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
import hk.ljx.fishhub.user.biz.constant.RoleConstants;
import hk.ljx.fishhub.user.biz.domain.dataobject.RoleDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserRoleDO;
import hk.ljx.fishhub.user.biz.domain.mapper.RoleDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.UserDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.UserRoleDOMapper;
import hk.ljx.fishhub.user.biz.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.enums.SexEnum;
import hk.ljx.fishhub.user.biz.model.vo.FindUserProfileReqVO;
import hk.ljx.fishhub.user.biz.model.vo.FindUserProfileRspVO;
import hk.ljx.fishhub.user.biz.model.vo.UpdateUserInfoReqVO;
import hk.ljx.fishhub.user.biz.rpc.CountRpcService;
import hk.ljx.fishhub.user.biz.rpc.DistributedIdGeneratorRpcService;
import hk.ljx.fishhub.user.biz.rpc.OssRpcService;
import hk.ljx.fishhub.user.biz.service.RolePermissionService;
import hk.ljx.fishhub.user.biz.service.UserService;
import hk.ljx.fishhub.user.dto.req.*;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.resp.ResolveLoginableUserRspDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionTemplate;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
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
    private final UserRoleDOMapper userRoleDOMapper;
    private final RoleDOMapper roleDOMapper;
    private final OssRpcService ossRpcService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Qualifier("fishhubTaskExecutor")
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final CountRpcService countRpcService;
    private final RocketMQTemplate rocketMQTemplate;
    private final RolePermissionService rolePermissionService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 用户信息本地缓存
     */
    private static final Cache<Long, FindUserByIdRspDTO> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

    /**
     * 用户主页信息本地缓存
     */
    private static final Cache<Long, FindUserProfileRspVO> PROFILE_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(5, TimeUnit.MINUTES) // 设置缓存条目在写入后 5 分钟过期
            .build();

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
        // 设置当前需要更新的用户 ID
        userDO.setId(userId);
        // 标识位：是否需要更新
        boolean needUpdate = false;

        // 头像
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

        // 昵称
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

        // 性别
        Integer sex = updateUserInfoReqVO.getSex();
        if (Objects.nonNull(sex)) {
            Preconditions.checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL.getErrorMessage());
            userDO.setSex(sex);
            needUpdate = true;
        }

        // 生日
        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (Objects.nonNull(birthday)) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        // 个人简介
        String introduction = updateUserInfoReqVO.getIntroduction();
        if (introduction != null) {
            Preconditions.checkArgument(introduction.length() <= 100,
                    ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getErrorMessage());
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        // 背景图
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
            // 更新用户信息
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);

            // 数据落库后立即清理当前节点和 Redis 缓存，避免读到旧资料。
            deleteUserLocalCache(userId);
            deleteUserRedisCache(userId);

            // 延时双删
            sendDelayDeleteUserRedisCacheMQ(userId);

            // 广播用于清理其他节点的本地缓存；失败不应影响已经成功的资料更新。
            try {
                rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_USER_LOCAL_CACHE, String.valueOf(userId));
            } catch (Exception e) {
                log.error("发送本地缓存清理消息失败, userId: {}", userId, e);
            }
        }
        return Response.success();
    }

    /**
     * 异步发送延时消息
     * @param userId
     */
    private void sendDelayDeleteUserRedisCacheMQ(Long userId) {
        Message<String> message = MessageBuilder.withPayload(String.valueOf(userId)).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE, message, new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                    }
                    @Override
                    public void onException(Throwable e) {
                        log.error("## 延时删除 Redis 用户缓存消息发送失败...", e);
                    }
                },
                3000, // 超时时间
                1 // 延迟级别，1 表示延时 1s
        );
    }

    /**
     * 删除 Redis 中的用户缓存
     * @param userId
     */
    private void deleteUserRedisCache(Long userId) {
        String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);
        String userProfileRedisKey = RedisKeyConstants.buildUserProfileKey(userId);

        // 批量删除
        stringRedisTemplate.delete(Arrays.asList(userInfoRedisKey, userProfileRedisKey));
    }

    @Override
    public void deleteUserLocalCache(Long userId) {
        LOCAL_CACHE.invalidate(userId);
        PROFILE_LOCAL_CACHE.invalidate(userId);
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

        UserDO newUser = UserDO.builder()
                .id(userId)
                .phone(phone)
                .fishhubId(fishhubId) // 自动生成小鱼号 ID
                .nickname("小鱼" + fishhubId) // 自动生成昵称, 如：小鱼10000
                .status(StatusEnum.ENABLE.getValue()) // 状态为启用
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue()) // 逻辑删除
                .build();

        // 3. 使用细粒度本地短事务保证：用户插入 + 默认角色绑定 的原子性
        UserDO finalUser = transactionTemplate.execute(status -> {
            if (userDOMapper.insertIfAbsent(newUser) == 0) {
                // 并发注册冲突时，查询已由另一线程成功创建的账号
                UserDO concurrentUser = userDOMapper.selectByPhone(phone);
                if (concurrentUser == null) {
                    throw new IllegalStateException("手机号账号创建后未找到");
                }
                return concurrentUser;
            }

            // 给该用户分配一个默认角色
            UserRoleDO userRoleDO = UserRoleDO.builder()
                    .userId(userId)
                    .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDeleted(DeletedEnum.NO.getValue())
                    .build();
            userRoleDOMapper.insert(userRoleDO);
            return newUser;
        });

        // 4. 事务成功提交后失效旧快照
        if (finalUser != null) {
            rolePermissionService.evict(finalUser.getId());
        }

        return resolvedLoginableUserResponse(finalUser);
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

        // 根据手机号查询用户信息
        UserDO userDO = userDOMapper.selectActiveByPhone(phone);

        // 判空
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
        // 获取当前请求对应的用户 ID
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

        // 先从本地缓存中查询
        FindUserByIdRspDTO findUserByIdRspDTOLocalCache = LOCAL_CACHE.getIfPresent(userId);
        if (Objects.nonNull(findUserByIdRspDTOLocalCache)) {
            log.info("==> 命中了本地缓存；{}", findUserByIdRspDTOLocalCache);
            return Response.success(findUserByIdRspDTOLocalCache);
        }

        // 用户缓存 Redis Key
        String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);

        // 再从 Redis 缓存中查询
        String userInfoRedisValue = stringRedisTemplate.opsForValue().get(userInfoRedisKey);

        // 若 Redis 缓存中存在该用户信息
        if (StringUtils.isNotBlank(userInfoRedisValue)) {
            if ("null".equals(userInfoRedisValue)) {
                throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
            }
            // 将存储的 Json 字符串转换成对象，并返回
            FindUserByIdRspDTO findUserByIdRspDTO = JsonUtils.parseObject(userInfoRedisValue, FindUserByIdRspDTO.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(userId, findUserByIdRspDTO);
            });
            return Response.success(findUserByIdRspDTO);
        }

        // 否则, 从数据库中查询
        // 根据用户 ID 查询用户信息
        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);

        // 判空
        if (Objects.isNull(userDO)) {
            threadPoolTaskExecutor.execute(() -> {
                // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long expireSeconds = CacheTtl.minutes(1, 1);
                stringRedisTemplate.opsForValue().set(userInfoRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserByIdRspDTO findUserByIdRspDTO = FindUserByIdRspDTO.builder()
                .id(userDO.getId())
                .nickName(userDO.getNickname())
                .avatar(userDO.getAvatar())
                .introduction(userDO.getIntroduction())
                .build();

        // 异步将用户信息存入 Redis 缓存，提升响应速度
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

    /**
     * 批量根据用户 ID 查询用户信息
     *
     * @param findUsersByIdsReqDTO
     * @return
     */
    @Override
    public Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        // 需要查询的用户 ID 集合
        List<Long> userIds = findUsersByIdsReqDTO.getIds();

        if (CollUtil.isEmpty(userIds)) {
            return Response.success(Collections.emptyList());
        }

        List<Long> distinctUserIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (CollUtil.isEmpty(distinctUserIds)) {
            return Response.success(Collections.emptyList());
        }

        List<String> redisKeys = distinctUserIds.stream()
                .map(RedisKeyConstants::buildUserInfoKey)
                .toList();

        // 批量查询 Redis
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
                FindUserByIdRspDTO dto = FindUserByIdRspDTO.builder()
                        .id(userDO.getId())
                        .nickName(userDO.getNickname())
                        .avatar(userDO.getAvatar())
                        .introduction(userDO.getIntroduction())
                        .build();
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
        // 要查询的用户 ID
        Long userId = findUserProfileReqVO.getUserId();

        // 若入参中用户 ID 为空，则查询当前登录用户
        if (Objects.isNull(userId)) {
            userId = LoginUserContextHolder.getUserId();
        }

        // 1. 优先查本地缓存
        if (!Objects.equals(userId, LoginUserContextHolder.getUserId())) { // 如果是用户本人查看自己的主页，则不走本地缓存（对本人保证实时性）
            FindUserProfileRspVO userProfileLocalCache = PROFILE_LOCAL_CACHE.getIfPresent(userId);
            if (Objects.nonNull(userProfileLocalCache)) {
                log.info("用户主页信息命中本地缓存，userId={}", userId);
                return Response.success(userProfileLocalCache);
            }
        }

        // 2. 再查询 Redis 缓存
        String userProfileRedisKey = RedisKeyConstants.buildUserProfileKey(userId);

        String userProfileJson = stringRedisTemplate.opsForValue().get(userProfileRedisKey);

        if (StringUtils.isNotBlank(userProfileJson)) {
            FindUserProfileRspVO findUserProfileRspVO = JsonUtils.parseObject(userProfileJson, FindUserProfileRspVO.class);
            // 异步同步到本地缓存
            syncUserProfile2LocalCache(userId, findUserProfileRspVO);
            // 如果是作者本人查看，保证计数的实时性
            authorGetActualCountData(userId, findUserProfileRspVO);

            return Response.success(findUserProfileRspVO);
        }

        // 3. 若 Redis 中无缓存，再查询数据库
        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);

        if (Objects.isNull(userDO)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        // 构建返参 VO
        FindUserProfileRspVO findUserProfileRspVO = FindUserProfileRspVO.builder()
                .userId(userDO.getId())
                .avatar(userDO.getAvatar())
                .nickname(userDO.getNickname())
                .fishhubId(userDO.getFishhubId())
                .sex(userDO.getSex())
                .birthday(userDO.getBirthday())
                .introduction(userDO.getIntroduction())
                .build();

        // 计算年龄
        LocalDate birthday = userDO.getBirthday();
        findUserProfileRspVO.setAge(Objects.isNull(birthday) ? null : DateUtils.calculateAge(birthday));

        // RPC: Feign 调用计数服务
        // 关注数、粉丝数、收藏与点赞总数；获得的点赞数、收藏数
        rpcCountServiceAndSetData(userId, findUserProfileRspVO);

        // 异步同步到 Redis 中
        syncUserProfile2Redis(userProfileRedisKey, findUserProfileRspVO);
        // 异步同步到本地缓存
        syncUserProfile2LocalCache(userId, findUserProfileRspVO);

        return Response.success(findUserProfileRspVO);
    }

    /**
     * Feign 调用计数服务, 并设置计数数据
     * @param userId
     * @param findUserProfileRspVO
     */
    private void rpcCountServiceAndSetData(Long userId, FindUserProfileRspVO findUserProfileRspVO) {
        FindUserCountsByIdRspDTO findUserCountsByIdRspDTO = countRpcService.findUserCountById(userId);

        if (Objects.nonNull(findUserCountsByIdRspDTO)) {
            Long fansTotal = findUserCountsByIdRspDTO.getFansTotal();
            Long followingTotal = findUserCountsByIdRspDTO.getFollowingTotal();
            Long likeTotal = findUserCountsByIdRspDTO.getLikeTotal();
            Long collectTotal = findUserCountsByIdRspDTO.getCollectTotal();
            Long noteTotal = findUserCountsByIdRspDTO.getNoteTotal();

            findUserProfileRspVO.setFansTotal(NumberUtils.formatNumberString(fansTotal));
            findUserProfileRspVO.setFollowingTotal(NumberUtils.formatNumberString(followingTotal));
            findUserProfileRspVO.setLikeAndCollectTotal(NumberUtils.formatNumberString(likeTotal + collectTotal));
            findUserProfileRspVO.setNoteTotal(NumberUtils.formatNumberString(noteTotal));
            findUserProfileRspVO.setLikeTotal(NumberUtils.formatNumberString(likeTotal));
            findUserProfileRspVO.setCollectTotal(NumberUtils.formatNumberString(collectTotal));
        }
    }

    /**
     * 作者本人获取真实的计数数据（保证实时性）
     * @param userId
     * @param findUserProfileRspVO
     */
    private void authorGetActualCountData(Long userId, FindUserProfileRspVO findUserProfileRspVO) {
        if (Objects.equals(userId, LoginUserContextHolder.getUserId())) {
            rpcCountServiceAndSetData(userId, findUserProfileRspVO);
        }
    }

    /**
     * 异步同步到本地缓存
     *
     * @param userId
     * @param findUserProfileRspVO
     */
    private void syncUserProfile2LocalCache(Long userId, FindUserProfileRspVO findUserProfileRspVO) {
        threadPoolTaskExecutor.submit(() -> {
            PROFILE_LOCAL_CACHE.put(userId, findUserProfileRspVO);
        });
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
                // 设置随机过期时间 (2小时以内)
                long expireTime = CacheTtl.hours(1, 1);

                // 将 VO 转为 Json 字符串写入到 Redis 中
                stringRedisTemplate.opsForValue().set(userProfileRedisKey, JsonUtils.toJsonString(findUserProfileRspVO), expireTime, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis 不可用，用户主页缓存写入失败", e);
            }
        });
    }
}
