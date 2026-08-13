package hk.ljx.framework.common.util;

import hk.ljx.framework.common.constant.DateConstants;

import java.time.*;
import java.time.temporal.ChronoUnit;


public class DateUtils {

    /**
     * LocalDateTime 转时间戳
     *
     * @param localDateTime
     * @return
     */
    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * LocalDateTime 转 String 字符串
     * @param time
     * @return
     */
    public static String localDateTime2String(LocalDateTime time) {
        return time.format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
    }

    /**
     * LocalDateTime 转友好的相对时间字符串
     * @param dateTime
     * @return
     */
    public static String formatRelativeTime(LocalDateTime dateTime) {
        // 当前时间
        LocalDateTime now = LocalDateTime.now();

        // 计算与当前时间的差距
        long daysDiff = ChronoUnit.DAYS.between(dateTime, now);
        long hoursDiff = ChronoUnit.HOURS.between(dateTime, now);
        long minutesDiff = ChronoUnit.MINUTES.between(dateTime, now);

        if (daysDiff < 1) {  // 如果是今天
            if (hoursDiff < 1) {  // 如果是几分钟前
                return minutesDiff + "分钟前";
            } else {  // 如果是几小时前
                return hoursDiff + "小时前";
            }
        } else if (daysDiff == 1) {  // 如果是昨天
            return "昨天 " + dateTime.format(DateConstants.DATE_FORMAT_H_M);
        } else if (daysDiff < 7) {  // 如果是最近一周
            return daysDiff + "天前";
        } else if (dateTime.getYear() == now.getYear()) {  // 如果是今年
            return dateTime.format(DateConstants.DATE_FORMAT_M_D);
        } else {  // 如果是去年或更早
            return dateTime.format(DateConstants.DATE_FORMAT_Y_M_D);
        }
    }

    /**
     * 计算年龄
     *
     * @param birthDate 出生日期（LocalDate）
     * @return 计算得到的年龄（以年为单位）
     */
    public static int calculateAge(LocalDate birthDate) {
        // 获取当前日期
        LocalDate currentDate = LocalDate.now();

        // 计算出生日期到当前日期的 Period 对象
        Period period = Period.between(birthDate, currentDate);

        // 返回完整的年份（即年龄）
        return period.getYears();
    }

}
