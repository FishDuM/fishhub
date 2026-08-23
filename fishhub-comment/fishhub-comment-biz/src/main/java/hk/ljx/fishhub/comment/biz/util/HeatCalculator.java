package hk.ljx.fishhub.comment.biz.util;

import java.math.BigDecimal;
import java.math.RoundingMode;


public class HeatCalculator {

    // 热度计算的权重配置
    private static final BigDecimal LIKE_WEIGHT = BigDecimal.valueOf(0.7);  // 点赞权重 70%
    private static final BigDecimal REPLY_WEIGHT = BigDecimal.valueOf(0.3); // 回复权重 30%

    public static BigDecimal calculateHeat(long likeCount, long replyCount) {
        BigDecimal heat = BigDecimal.valueOf(likeCount).multiply(LIKE_WEIGHT)
                .add(BigDecimal.valueOf(replyCount).multiply(REPLY_WEIGHT));
        return heat.setScale(2, RoundingMode.HALF_UP);
    }

}
