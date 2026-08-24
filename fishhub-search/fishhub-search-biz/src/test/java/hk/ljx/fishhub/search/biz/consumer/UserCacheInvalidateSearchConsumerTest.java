package hk.ljx.fishhub.search.biz.consumer;

import hk.ljx.fishhub.user.client.UserClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserCacheInvalidateSearchConsumerTest {

    @Test
    void shouldInvalidateLocalCacheOnUserChangedMessage() {
        UserCacheInvalidateSearchConsumer consumer = new UserCacheInvalidateSearchConsumer();

        assertDoesNotThrow(() -> consumer.onMessage("12345"));
        assertDoesNotThrow(() -> consumer.onMessage(""));
        assertDoesNotThrow(() -> consumer.onMessage("invalid-id"));
    }
}
