package hk.ljx.fishhub.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static hk.ljx.framework.common.constant.GlobalConstants.USER_ID;
import static hk.ljx.fishhub.gateway.auth.SaTokenConfigure.USER_ID_ATTR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddUserId2HeaderFilterTest {

    @Mock
    private ServerWebExchange exchange;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpRequest.Builder requestBuilder;
    @Mock
    private ServerHttpRequest mutatedRequest;
    @Mock
    private ServerWebExchange.Builder exchangeBuilder;
    @Mock
    private ServerWebExchange mutatedExchange;
    @Mock
    private GatewayFilterChain chain;
    @Mock
    private Mono<Void> mono;

    @Test
    void shouldReadUserIdFromExchangeAttributeWithoutReParsingToken() {
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getAttribute(USER_ID_ATTR)).thenReturn("12");
        when(request.mutate()).thenReturn(requestBuilder);
        doAnswer(inv -> {
            ((Consumer<HttpHeaders>) inv.getArgument(0)).accept(new HttpHeaders());
            return requestBuilder;
        }).when(requestBuilder).headers(any());
        when(requestBuilder.header(USER_ID, "12")).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mutatedRequest);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(mutatedRequest)).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(mutatedExchange);
        when(chain.filter(mutatedExchange)).thenReturn(mono);

        AddUserId2HeaderFilter filter = new AddUserId2HeaderFilter();
        Mono<Void> result = filter.filter(exchange, chain);

        assertEquals(mono, result);
        verify(requestBuilder).header(USER_ID, "12");
    }
}
