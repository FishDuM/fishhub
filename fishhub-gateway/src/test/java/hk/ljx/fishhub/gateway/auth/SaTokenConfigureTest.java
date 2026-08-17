package hk.ljx.fishhub.gateway.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebExchange;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaTokenConfigureTest {

    @Test
    void shouldPutLoginIdIntoExchangeAttributes() {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        Map<String, Object> attributes = new HashMap<>();
        when(exchange.getAttributes()).thenReturn(attributes);

        SaTokenConfigure.putLoginIdAttribute(exchange, 42L);

        assertEquals("42", attributes.get(SaTokenConfigure.USER_ID_ATTR));
    }

    @Test
    void shouldTreatLoginWhitelistPathsAsPublic() {
        org.junit.jupiter.api.Assertions.assertTrue(SaTokenConfigure.isWhitelisted("/auth/login"));
        org.junit.jupiter.api.Assertions.assertTrue(SaTokenConfigure.isWhitelisted("/note/channel/list"));
        org.junit.jupiter.api.Assertions.assertTrue(SaTokenConfigure.isWhitelisted("/comment/comment/list"));
        org.junit.jupiter.api.Assertions.assertFalse(SaTokenConfigure.isWhitelisted("/note/note/publish"));
        org.junit.jupiter.api.Assertions.assertFalse(SaTokenConfigure.isWhitelisted("/comment/comment/publish"));
    }

    @Test
    void shouldIgnoreNullLoginId() {
        ServerWebExchange exchange = mock(ServerWebExchange.class);

        SaTokenConfigure.putLoginIdAttribute(exchange, null);

        verify(exchange, never()).getAttributes();
    }
}
