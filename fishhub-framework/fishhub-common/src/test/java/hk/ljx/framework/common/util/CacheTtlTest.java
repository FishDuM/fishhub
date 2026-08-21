package hk.ljx.framework.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheTtlTest {

    @Test
    void basePlusRandomShouldNeverReturnZero() {
        for (int i = 0; i < 10000; i++) {
            long seconds = CacheTtl.basePlusRandom(0, 5);
            assertTrue(seconds >= 1, "TTL should be at least 1 second");
        }
    }
}
