package hk.ljx.fishhub.user.biz.util;

import hk.ljx.framework.common.util.ParamUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamUtilsTest {

    @Test
    void shouldAcceptFishhubIdsThatMatchTheProductRule() {
        assertTrue(ParamUtils.checkFishhubId("fish10100"));
        assertTrue(ParamUtils.checkFishhubId("fish_01"));
        assertTrue(ParamUtils.checkFishhubId("fish1892182918291829101")); // 支持雪花算法小鱼号(23位)
    }

    @Test
    void shouldRejectShortNumericOrUnsupportedFishhubIds() {
        assertFalse(ParamUtils.checkFishhubId(null));
        assertFalse(ParamUtils.checkFishhubId("1"));
        assertFalse(ParamUtils.checkFishhubId("123456"));
        assertFalse(ParamUtils.checkFishhubId("fish-01"));
        assertFalse(ParamUtils.checkFishhubId("fish_too_long_1234567890123456789012345")); // > 32 字符被拒绝
    }
}
