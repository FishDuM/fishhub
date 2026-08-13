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
        LocalDateTime now = LocalDateTime.now();
        long daysDiff = ChronoUnit.DAYS.between(dateTime, now);
        long hoursDiff = ChronoUnit.HOURS.between(dateTime, now);
        long minutesDiff = ChronoUnit.MINUTES.between(dateTime, now);

        if (daysDiff < 1) {
            if (hoursDiff < 1) {
                return minutesDiff + "分钟前";
            } else {
                return hoursDiff + "小时前";
            }
        } else if (daysDiff == 1) {
            return "昨天 " + dateTime.format(DateConstants.DATE_FORMAT_H_M);
        } else if (daysDiff < 7) {
            return daysDiff + "天前";
        } else if (dateTime.getYear() == now.getYear()) {
            return dateTime.format(DateConstants.DATE_FORMAT_M_D);
        } else {
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
        LocalDate currentDate = LocalDate.now();
        Period period = Period.between(birthDate, currentDate);
        return period.getYears();
    }

}
