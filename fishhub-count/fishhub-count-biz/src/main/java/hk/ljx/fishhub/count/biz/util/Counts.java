package hk.ljx.fishhub.count.biz.util;

/**
 * 计数读侧防护：增量 SQL 已去钳制（保证可交换），负数只可能来自 bug，
 * 在出口处钳为 0，避免脏值直接透出给用户。
 */
public final class Counts {

    private Counts() {
    }

    public static long clamp0(Long value) {
        return value == null || value < 0 ? 0L : value;
    }
}
