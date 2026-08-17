package hk.ljx.fishhub.user.biz.runner;

import hk.ljx.fishhub.user.biz.domain.mapper.UserRoleDOMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import static hk.ljx.fishhub.user.biz.constant.RoleConstants.COMMON_USER_ROLE_ID;

/**
 * 启动时为历史导入且无角色的用户补齐系统默认角色（保留原全量预热 Runner 的存量数据修复行为）。
 */
@Component
@Slf4j
public class DefaultRoleBackfillRunner implements ApplicationRunner {

    @Resource
    private UserRoleDOMapper userRoleDOMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            userRoleDOMapper.insertDefaultRoleForUsersWithoutRole(COMMON_USER_ROLE_ID);
        } catch (Exception e) {
            log.error("==> 历史用户默认角色补齐失败: ", e);
        }
    }
}
