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
    }

    @Test
    void shouldRejectShortNumericOrUnsupportedFishhubIds() {
        assertFalse(ParamUtils.checkFishhubId(null));
        assertFalse(ParamUtils.checkFishhubId("1"));
        assertFalse(ParamUtils.checkFishhubId("123456"));
        assertFalse(ParamUtils.checkFishhubId("fish-01"));
        assertFalse(ParamUtils.checkFishhubId("fishhub_id_123456"));
    }
}
