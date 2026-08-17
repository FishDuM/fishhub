package hk.ljx.fishhub.gateway.auth;

import cn.dev33.satoken.session.SaSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StpInterfaceImplTest {

    private final Map<Object, SaSession> sessions = new HashMap<>();

    private final StpInterfaceImpl stpInterface = new StpInterfaceImpl() {
        @Override
        protected SaSession loadSession(Object loginId) {
            return sessions.get(loginId);
        }
    };

    @Test
    void shouldReadRolesAndPermissionsFromSession() {
        SaSession session = new SaSession();
        session.setId("test-session-100");
        session.set(SaSession.ROLE_LIST, List.of("common"));
        session.set(SaSession.PERMISSION_LIST, List.of("note:publish"));
        sessions.put(100L, session);

        assertEquals(List.of("common"), stpInterface.getRoleList(100L, "login"));
        assertEquals(List.of("note:publish"), stpInterface.getPermissionList(100L, "login"));
    }

    @Test
    void shouldReturnEmptyWhenSessionHasNoRoleData() {
        sessions.put(100L, new SaSession());

        assertEquals(List.of(), stpInterface.getRoleList(100L, "login"));
        assertEquals(List.of(), stpInterface.getPermissionList(100L, "login"));
    }
}
