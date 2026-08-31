package hk.ljx.framework.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberUtilsTest {

    @Test
    void shouldFormatNumberStringCorrectly() {
        assertEquals("0", NumberUtils.formatNumberString(0L));
        assertEquals("999", NumberUtils.formatNumberString(999L));
        assertEquals("9999", NumberUtils.formatNumberString(9999L));
        assertEquals("1万", NumberUtils.formatNumberString(10000L));
        assertEquals("1.2万", NumberUtils.formatNumberString(12345L));
        assertEquals("9999.9万", NumberUtils.formatNumberString(99999000L));
        assertEquals("1亿", NumberUtils.formatNumberString(100000000L));
        assertEquals("3.5亿", NumberUtils.formatNumberString(356000000L));
    }
}
