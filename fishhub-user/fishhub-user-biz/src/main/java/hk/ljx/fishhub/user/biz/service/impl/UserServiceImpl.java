package hk.ljx.fishhub.user.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
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
import hk.ljx.fishhub.user.biz.service.UserService;
import hk.ljx.fishhub.user.dto.req.*;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.resp.ResolveLoginableUserRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
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
public class UserServiceImpl implements UserService {

    @Resource
    private UserDOMapper userDOMapper;
    @Resource
    private UserRoleDOMapper userRoleDOMapper;
    @Resource
    private RoleDOMapper roleDOMapper;
    @Resource
    private OssRpcService ossRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource(name = "fishhubTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private UserDOMapper userMapper;
    @Resource
    private CountRpcService countRpcService;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

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
        redisTemplate.delete(Arrays.asList(userInfoRedisKey, userProfileRedisKey));
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
    @Transactional(rollbackFor = Exception.class)
    public Response<ResolveLoginableUserRspDTO> resolveOrRegisterLoginableUser(ResolveLoginableUserReqDTO request) {
        String phone = request.getPhone();

        // 唯一索引上的行锁/间隙锁使同一手机号的“查询或注册”串行，避免并发重复创建。
        UserDO userDO1 = userDOMapper.selectByPhoneForUpdate(phone);

        log.info("手机号查询完成，found={}", userDO1 != null);

        if (Objects.nonNull(userDO1)) {
            return resolvedLoginableUserResponse(userDO1);
        }

        // 否则注册新用户
        String fishhubId = distributedIdGeneratorRpcService.getFishhubId();

        // RPC: 调用分布式 ID 生成服务生成用户 ID
        String userIdStr = distributedIdGeneratorRpcService.getUserId();
        Long userId = Long.valueOf(userIdStr);

        UserDO userDO = UserDO.builder()
                .id(userId)
                .phone(phone)
                .fishhubId(fishhubId) // 自动生成小鱼号 ID
                .nickname("小鱼" + fishhubId) // 自动生成昵称, 如：小鱼10000
                .status(StatusEnum.ENABLE.getValue()) // 状态为启用
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue()) // 逻辑删除
                .build();

        if (userDOMapper.insertIfAbsent(userDO) == 0) {
            UserDO existingUser = userDOMapper.selectByPhoneForUpdate(phone);
            if (existingUser == null) {
                throw new IllegalStateException("手机号账号创建后未找到");
            }
            return resolvedLoginableUserResponse(existingUser);
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

        RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);

        // 将该用户的角色 ID 存入 Redis 中
        List<String> roles = new ArrayList<>(1);
        roles.add(roleDO.getRoleKey());

        String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
        stringRedisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));

        return resolvedLoginableUserResponse(userDO);
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
        String userInfoRedisValue = (String) redisTemplate.opsForValue().get(userInfoRedisKey);

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
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(userInfoRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
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
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            redisTemplate.opsForValue()
                    .set(userInfoRedisKey, JsonUtils.toJsonString(findUserByIdRspDTO), expireSeconds, TimeUnit.SECONDS);
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

        List<String> redisKeys = userIds.stream()
                .map(RedisKeyConstants::buildUserInfoKey)
                .toList();

        // 先从 Redis 缓存中查, multiGet 批量查询提升性能
        List<Object> redisValues = redisTemplate.opsForValue().multiGet(redisKeys);
        // 如果缓存中不为空
        if (CollUtil.isNotEmpty(redisValues)) {
            // 过滤掉为空的数据
            redisValues = redisValues.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> !"null".equals(String.valueOf(value)))
                    .toList();
        }

        // 返参
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = Lists.newArrayList();

        // 将过滤后的缓存集合，转换为 DTO 返参实体类
        if (CollUtil.isNotEmpty(redisValues)) {
            findUserByIdRspDTOS.addAll(redisValues.stream()
                    .map(value -> JsonUtils.parseObject(String.valueOf(value), FindUserByIdRspDTO.class))
                    .filter(Objects::nonNull)
                    .toList());
        }

        // 如果被查询的用户信息，都在 Redis 缓存中, 则直接返回
        if (CollUtil.size(userIds) == CollUtil.size(findUserByIdRspDTOS)) {
            return Response.success(orderUsersByRequestIds(userIds, findUserByIdRspDTOS));
        }

        // 还有另外两种情况：一种是缓存里没有用户信息数据，还有一种是缓存里数据不全，需要从数据库中补充
        // 筛选出缓存里没有的用户数据，去查数据库
        List<Long> userIdsNeedQuery = null;

        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            // 将 findUserInfoByIdRspDTOS 集合转 Map
            Map<Long, FindUserByIdRspDTO> map = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));

            // 筛选出需要查 DB 的用户 ID
            userIdsNeedQuery = userIds.stream()
                    .filter(id -> Objects.isNull(map.get(id)))
                    .toList();
        } else { // 缓存中一条用户信息都没查到，则提交的用户 ID 集合都需要查数据库
            userIdsNeedQuery = userIds;
        }

        // 从数据库中批量查询
        List<UserDO> userDOS = null;
        if (CollUtil.isNotEmpty(userIdsNeedQuery)) {
            userDOS = userDOMapper.selectByIds(userIdsNeedQuery);
        }

        List<FindUserByIdRspDTO> findUserByIdRspDTOS2 = null;

        // 若数据库查询的记录不为空
        if (CollUtil.isNotEmpty(userDOS)) {
            // DO 转 DTO
            findUserByIdRspDTOS2 = userDOS.stream()
                    .map(userDO -> FindUserByIdRspDTO.builder()
                            .id(userDO.getId())
                            .nickName(userDO.getNickname())
                            .avatar(userDO.getAvatar())
                            .introduction(userDO.getIntroduction())
                            .build())
                    .collect(Collectors.toList());

            // 异步线程将用户信息同步到 Redis 中
            List<FindUserByIdRspDTO> finalFindUserByIdRspDTOS = findUserByIdRspDTOS2;
            List<UserDO> finalUserDOS = userDOS;
            threadPoolTaskExecutor.submit(() -> {
                // DTO 集合转 Map
                Map<Long, FindUserByIdRspDTO> map = finalFindUserByIdRspDTOS.stream()
                        .collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));

                // 执行 pipeline 操作
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        for (UserDO userDO : finalUserDOS) {
                            Long userId = userDO.getId();

                            // 用户信息缓存 Redis Key
                            String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);

                            // DTO 转 JSON 字符串
                            FindUserByIdRspDTO findUserInfoByIdRspDTO = map.get(userId);
                            String value = JsonUtils.toJsonString(findUserInfoByIdRspDTO);

                            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
                            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                            operations.opsForValue().set(userInfoRedisKey, value, expireSeconds, TimeUnit.SECONDS);
                        }
                        return null;
                    }
                });
            });
        }

        // 合并数据
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS2)) {
            findUserByIdRspDTOS.addAll(findUserByIdRspDTOS2);
        }

        return Response.success(orderUsersByRequestIds(userIds, findUserByIdRspDTOS));
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

        String userProfileJson = (String) redisTemplate.opsForValue().get(userProfileRedisKey);

        if (StringUtils.isNotBlank(userProfileJson)) {
            FindUserProfileRspVO findUserProfileRspVO = JsonUtils.parseObject(userProfileJson, FindUserProfileRspVO.class);
            // 异步同步到本地缓存
            syncUserProfile2LocalCache(userId, findUserProfileRspVO);
            // 如果是作者本人查看，保证计数的实时性
            authorGetActualCountData(userId, findUserProfileRspVO);

            return Response.success(findUserProfileRspVO);
        }

        // 3. 若 Redis 中无缓存，再查询数据库
        UserDO userDO = userMapper.selectByPrimaryKey(userId);

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
            // 设置随机过期时间 (2小时以内)
            long expireTime = 60*60 + RandomUtil.randomInt(60 * 60);

            // 将 VO 转为 Json 字符串写入到 Redis 中
            redisTemplate.opsForValue().set(userProfileRedisKey, JsonUtils.toJsonString(findUserProfileRspVO), expireTime, TimeUnit.SECONDS);
        });
    }
}
