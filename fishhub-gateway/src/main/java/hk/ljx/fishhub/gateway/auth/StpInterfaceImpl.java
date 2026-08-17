package hk.ljx.fishhub.gateway.auth;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 角色/权限读取：数据在登录时由 auth 服务写入 sa-token 会话（会话存 Redis、带 TTL），
 * 这里只做会话读取，无独立角色权限缓存层。
 */
@Component
@Slf4j
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return readSessionList(loginId, SaSession.PERMISSION_LIST);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return readSessionList(loginId, SaSession.ROLE_LIST);
    }

    private List<String> readSessionList(Object loginId, String key) {
        Object value = loadSession(loginId).get(key);
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream().map(String::valueOf).toList();
    }

    /**
     * 会话获取钩子，便于测试替换
     */
    protected SaSession loadSession(Object loginId) {
        return StpUtil.getSessionByLoginId(loginId);
    }
}
