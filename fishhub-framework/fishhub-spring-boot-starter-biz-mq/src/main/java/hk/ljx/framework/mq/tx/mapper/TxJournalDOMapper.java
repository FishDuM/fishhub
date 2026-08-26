package hk.ljx.framework.mq.tx.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TxJournalDOMapper {

    @Insert("INSERT IGNORE INTO t_tx_journal (tx_id, create_time) VALUES (#{txId}, NOW())")
    int insertIgnore(@Param("txId") String txId);

    @Select("SELECT COUNT(1) FROM t_tx_journal WHERE tx_id = #{txId}")
    int countByTxId(@Param("txId") String txId);

    @Delete("DELETE FROM t_tx_journal WHERE create_time &lt; DATE_SUB(NOW(), INTERVAL #{hours} HOUR) ORDER BY create_time ASC LIMIT #{batchSize}")
    int purgeOlderThanHours(@Param("hours") int hours, @Param("batchSize") int batchSize);
}
