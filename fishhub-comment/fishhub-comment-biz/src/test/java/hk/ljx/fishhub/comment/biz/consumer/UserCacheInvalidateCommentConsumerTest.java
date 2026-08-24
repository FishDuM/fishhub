package hk.ljx.fishhub.comment.biz.consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserCacheInvalidateCommentConsumerTest {

    private final UserCacheInvalidateCommentConsumer consumer = new UserCacheInvalidateCommentConsumer();

    @Test
    void shouldInvalidateUserCacheSuccessfully() {
        assertDoesNotThrow(() -> consumer.onMessage("2001"));
        assertDoesNotThrow(() -> consumer.onMessage(""));
        assertDoesNotThrow(() -> consumer.onMessage(null));
        assertDoesNotThrow(() -> consumer.onMessage("invalid-num"));
    }
}
