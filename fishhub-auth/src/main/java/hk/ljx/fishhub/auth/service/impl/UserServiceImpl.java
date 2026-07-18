package hk.ljx.fishhub.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.common.base.Preconditions;
import hk.ljx.fishhub.auth.constant.RedisKeyConstants;
import hk.ljx.fishhub.auth.constant.RoleConstants;
import hk.ljx.fishhub.auth.domain.dataobject.RoleDO;
import hk.ljx.fishhub.auth.domain.dataobject.UserDO;
import hk.ljx.fishhub.auth.domain.dataobject.UserRoleDO;
import hk.ljx.fishhub.auth.domain.mapper.RoleDOMapper;
import hk.ljx.fishhub.auth.domain.mapper.UserDOMapper;
import hk.ljx.fishhub.auth.domain.mapper.UserRoleDOMapper;
import hk.ljx.fishhub.auth.enums.LoginTypeEnum;
import hk.ljx.fishhub.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.auth.modal.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.auth.service.UserService;
import hk.ljx.fishhub.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.enums.DeletedEnum;
import hk.ljx.framework.common.enums.StatusEnum;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static hk.ljx.fishhub.auth.constant.RedisKeyConstants.FISHHUB_ID_GENERATOR_KEY;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UserDOMapper userDOMapper;

    @Resource
    private UserRoleDOMapper userRoleDOMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RoleDOMapper roleDOMapper;

    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        String password = userLoginReqVO.getPassword();
        Integer type = userLoginReqVO.getType();
        Long userId = null;

        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);
        switch (loginTypeEnum) {
            case VERIFICATION_CODE: // 验证码登录
                String verificationCode = userLoginReqVO.getCode();
                Preconditions.checkArgument(StringUtils.isNotBlank(verificationCode), "验证码不能为空");
                // 校验验证码
                String key = RedisKeyConstants.buildVerificationCodeKey(phone);
                String sentCode = (String) redisTemplate.opsForValue().get(key);
                if (!StringUtils.equals(verificationCode, sentCode)) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }
                // 校验用户是否存在
                UserDO userDO = userDOMapper.selectByPhone(phone);
                log.info("用户是否注册, phone: {}, userDO: {}", phone, JsonUtils.toJsonString(userDO));
                if (Objects.isNull(userDO)) {
                    // 未注册则创建用户
                    userId = registerUser(phone);
                } else {
                    // 已注册则获取
                    userId = userDO.getId();
                }
                break;
            case PASSWORD:
                // todo 密码登录
                break;
            default:
                break;
        }

        // SaToken 登录用户, 入参为用户 ID
        StpUtil.login(userId);
        // 获取 Token 令牌
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        // 返回 Token 令牌
        return Response.success(tokenInfo.tokenValue);
    }

    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();
        log.info("== 用户退出登录 ID: {}", userId);
        StpUtil.logout(userId);
        return Response.success();
    }

    /**
     * 用户手机号注册
     * @param phone
     * @return
     */
    public Long registerUser(String phone) {
        return transactionTemplate.execute(status -> {
            try {
                // 获取全局自增 id
                Long fishhubId = redisTemplate.opsForValue().increment(FISHHUB_ID_GENERATOR_KEY);
                // 创建用户
                UserDO userDO = UserDO.builder()
                        .phone(phone)
                        .fishhubId(String.valueOf(fishhubId))
                        .nickname("小鱼" + fishhubId)
                        .status(StatusEnum.ENABLE.getValue())
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .isDeleted(DeletedEnum.NO.getValue())
                        .build();
                userDOMapper.insert(userDO);
                Long userId = userDO.getId();
                // 分配角色
                UserRoleDO userRoleDO = UserRoleDO.builder()
                        .userId(userId)
                        .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .isDeleted(DeletedEnum.NO.getValue())
                        .build();
                userRoleDOMapper.insert(userRoleDO);

                RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);
                // 将该用户的角色 id 存入 redis 中
                List<String> roles = new ArrayList<>(1);
                roles.add(roleDO.getRoleKey());
                String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
                redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));

                return userId;
            }catch (Exception e){
                status.setRollbackOnly(); // 异常回滚
                log.error("系统注册用户异常", e);
                return null;
            }
        });
    }
}
