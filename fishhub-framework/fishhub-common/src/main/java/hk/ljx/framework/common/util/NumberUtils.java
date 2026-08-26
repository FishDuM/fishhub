package hk.ljx.framework.common.util;

import cn.hutool.core.util.NumberUtil;

import java.math.RoundingMode;

/**
 * 数字格式化工具类
 */
public final class NumberUtils {

    private NumberUtils() {
    }

    /**
     * 数字转换为易读字符串（如 1.2万、3.5亿）
     */
    public static String formatNumberString(long number) {
        if (number < 10000) {
            return String.valueOf(number);
        } else if (number < 100000000) {
            return NumberUtil.decimalFormat("#.#", number / 10000.0, RoundingMode.DOWN) + "万";
        } else {
            return NumberUtil.decimalFormat("#.#", number / 100000000.0, RoundingMode.DOWN) + "亿";
        }
    }
}
