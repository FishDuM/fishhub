package hk.ljx.fishhub.gateway.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(objectMapper);

    @Test
    void shouldReturnInternalServerErrorForUnexpectedException() throws Exception {
        MockServerWebExchange exchange = createExchange();

        exceptionHandler.handle(exchange, new RuntimeException("unexpected")).block();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        JsonNode body = readResponseBody(exchange);
        assertFalse(body.get("success").asBoolean());
        assertEquals("500", body.get("errorCode").asText());
    }

    @Test
    void shouldKeepStatusFromResponseStatusException() throws Exception {
        MockServerWebExchange exchange = createExchange();

        exceptionHandler.handle(exchange,
                new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在")).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        JsonNode body = readResponseBody(exchange);
        assertFalse(body.get("success").asBoolean());
        assertEquals("404", body.get("errorCode").asText());
        assertEquals("资源不存在", body.get("message").asText());
    }

    private MockServerWebExchange createExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    }

    private JsonNode readResponseBody(MockServerWebExchange exchange) throws Exception {
        return objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
    }
}
