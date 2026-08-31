package hk.ljx.framework.mq.idempotent.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MqConsumeRecordDOMapper {

    @Select("SELECT COUNT(1) FROM t_mq_consume_record WHERE consumer_group = #{consumerGroup} AND message_key = #{messageKey}")
    int exists(@Param("consumerGroup") String consumerGroup, @Param("messageKey") String messageKey);

    @Insert("INSERT INTO t_mq_consume_record (consumer_group, message_key, create_time) VALUES (#{consumerGroup}, #{messageKey}, NOW())")
    int insert(@Param("consumerGroup") String consumerGroup, @Param("messageKey") String messageKey);

    @Select("<script>" +
            "SELECT message_key FROM t_mq_consume_record " +
            "WHERE consumer_group = #{consumerGroup} AND message_key IN " +
            "<foreach collection='messageKeys' item='key' open='(' separator=',' close=')'>" +
            "#{key}" +
            "</foreach>" +
            "</script>")
    List<String> findExisting(@Param("consumerGroup") String consumerGroup, @Param("messageKeys") List<String> messageKeys);

    @Insert("<script>" +
            "INSERT IGNORE INTO t_mq_consume_record (consumer_group, message_key, create_time) VALUES " +
            "<foreach collection='messageKeys' item='key' separator=','>" +
            "(#{consumerGroup}, #{key}, NOW())" +
            "</foreach>" +
            "</script>")
    int insertIgnoreBatch(@Param("consumerGroup") String consumerGroup, @Param("messageKeys") List<String> messageKeys);

    @Delete("DELETE FROM t_mq_consume_record WHERE create_time &lt; DATE_SUB(NOW(), INTERVAL #{days} DAY) ORDER BY id ASC LIMIT #{batchSize}")
    int purgeOlderThanDays(@Param("days") int days, @Param("batchSize") int batchSize);
}
