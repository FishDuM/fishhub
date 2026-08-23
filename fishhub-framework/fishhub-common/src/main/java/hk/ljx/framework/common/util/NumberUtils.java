package hk.ljx.framework.common.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class NumberUtils {

    private static final ThreadLocal<DecimalFormat> DF_HOLDER = ThreadLocal.withInitial(() -> {
        DecimalFormat df = new DecimalFormat("#.#");
        df.setRoundingMode(RoundingMode.DOWN);
        return df;
    });

    private NumberUtils() {
    }

    /**
     * 数字转换为易读字符串（如 1.2万、3.5亿）
     */
    public static String formatNumberString(long number) {
        if (number < 10000) {
            return String.valueOf(number);
        } else if (number < 100000000) {
            double result = number / 10000.0;
            return DF_HOLDER.get().format(result) + "万";
        } else {
            double result = number / 100000000.0;
            return DF_HOLDER.get().format(result) + "亿";
        }
    }
}
