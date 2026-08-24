package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.fishhub.user.client.UserClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserCacheInvalidateNoteConsumerTest {

    private final UserCacheInvalidateNoteConsumer consumer = new UserCacheInvalidateNoteConsumer();

    @Test
    void shouldInvalidateUserCacheSuccessfully() {
        assertDoesNotThrow(() -> consumer.onMessage("1001"));
        assertDoesNotThrow(() -> consumer.onMessage(""));
        assertDoesNotThrow(() -> consumer.onMessage(null));
        assertDoesNotThrow(() -> consumer.onMessage("invalid-num"));
    }
}
