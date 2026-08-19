package hk.ljx.fishhub.search.biz.canal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Canal 投递到 RocketMQ 的标准 FlatMessage JSON 消息结构
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CanalFlatMessageDTO implements Serializable {

    /**
     * 变更的数据列表（每行为一个 Map<列名, 值>）
     */
    private List<Map<String, Object>> data;

    /**
     * 数据库名称 (Schema)
     */
    private String database;

    /**
     * 执行耗时
     */
    private Long es;

    /**
     * 消息唯一 ID
     */
    private Long id;

    /**
     * 是否是 DDL 语句
     */
    private Boolean isDdl;

    /**
     * MySQL 字段类型映射
     */
    private Map<String, String> mysqlType;

    /**
     * 旧数据（UPDATE 时变更前的列值）
     */
    private List<Map<String, Object>> old;

    /**
     * 主键名称列表
     */
    private List<String> pkNames;

    /**
     * 执行的 SQL
     */
    private String sql;

    /**
     * 表名
     */
    private String table;

    /**
     * 时间戳
     */
    private Long ts;

    /**
     * 操作类型: INSERT, UPDATE, DELETE
     */
    private String type;
}
