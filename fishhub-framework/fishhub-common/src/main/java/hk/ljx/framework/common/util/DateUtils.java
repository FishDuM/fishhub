package hk.ljx.framework.common.util;

import hk.ljx.framework.common.constant.DateConstants;

import java.time.*;
import java.time.temporal.ChronoUnit;


public class DateUtils {

    /**
     * LocalDateTime 转时间戳（默认系统时区/东八区）
     *
     * @param localDateTime
     * @return
     */
    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        if (localDateTime == null) return 0L;
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * LocalDateTime 转 String 字符串
     * @param time
     * @return
     */
    public static String localDateTime2String(LocalDateTime time) {
        return time == null ? "" : time.format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
    }

    /**
     * LocalDateTime 转友好的相对时间字符串
     * @param dateTime
     * @return
     */
    public static String formatRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        LocalDateTime now = LocalDateTime.now();
        long minutesDiff = ChronoUnit.MINUTES.between(dateTime, now);
        long hoursDiff = ChronoUnit.HOURS.between(dateTime, now);

        LocalDate targetDate = dateTime.toLocalDate();
        LocalDate today = now.toLocalDate();
        long calendarDaysDiff = ChronoUnit.DAYS.between(targetDate, today);

        if (calendarDaysDiff == 0) {
            if (minutesDiff < 1) {
                return "刚刚";
            } else if (minutesDiff < 60) {
                return minutesDiff + "分钟前";
            } else {
                return hoursDiff + "小时前";
            }
        } else if (calendarDaysDiff == 1) {
            return "昨天 " + dateTime.format(DateConstants.DATE_FORMAT_H_M);
        } else if (calendarDaysDiff < 7) {
            return calendarDaysDiff + "天前";
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

    /**
     * 复合自适应解析时间字符串为 LocalDateTime
     * 支持 "yyyy-MM-dd HH:mm:ss"、"yyyy-MM-dd'T'HH:mm:ss"、"yyyy-MM-dd'T'HH:mm:ss.SSS"、ISO_DATE_TIME 带时区等格式
     *
     * @param timeStr 时间字符串
     * @return 解析后的 LocalDateTime，若解析失败或为空则返回 null
     */
    public static LocalDateTime parseFlexibleLocalDateTime(String timeStr) {
        return parseFlexibleLocalDateTime(timeStr, null);
    }

    /**
     * 复合自适应解析时间字符串为 LocalDateTime，支持提供默认值兜底
     *
     * @param timeStr 时间字符串
     * @param defaultVal 兜底默认值
     * @return 解析后的 LocalDateTime
     */
    public static LocalDateTime parseFlexibleLocalDateTime(String timeStr, LocalDateTime defaultVal) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return defaultVal;
        }
        try {
            java.time.temporal.TemporalAccessor accessor = DateConstants.DATE_FORMAT_FLEXIBLE.parseBest(
                    timeStr.trim(),
                    ZonedDateTime::from,
                    LocalDateTime::from,
                    Instant::from
            );
            if (accessor instanceof ZonedDateTime zdt) {
                return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } else if (accessor instanceof LocalDateTime ldt) {
                return ldt;
            } else if (accessor instanceof Instant instant) {
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            }
            return defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

}
