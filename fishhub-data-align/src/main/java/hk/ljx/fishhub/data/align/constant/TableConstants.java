package hk.ljx.fishhub.data.align.constant;


public class TableConstants {

    /**
     * 表名中的分隔符
     */
    private static final String TABLE_NAME_SEPARATE = "_";

    /**
     * 拼接表名后缀
     * @param hashKey
     * @return
     */
    public static String buildTableNameSuffix(String date, long hashKey) {
        // 拼接完整的表名
        return date + TABLE_NAME_SEPARATE + hashKey;
    }

    /**
     * 计算本次任务需处理的分片区间 [start, end)
     * 分片广播且执行器数量与表分片数一致时，每台执行器处理一个分片；单实例时遍历全部分片表
     * @param shardIndex 分片序号
     * @param shardTotal 总分片数
     * @param tableShards 日增量表分片数
     * @return 分片区间（含头不含尾）
     */
    public static int[] computeShardRange(int shardIndex, int shardTotal, int tableShards) {
        if (shardTotal == 1) {
            return new int[] {0, tableShards};
        }
        if (shardTotal != tableShards) {
            throw new IllegalStateException("执行器数量(" + shardTotal + ")与日增量表分片数(" + tableShards + ")不一致，无法对齐");
        }
        return new int[] {shardIndex, shardIndex + 1};
    }

}
