package hk.ljx.fishhub.count.biz.domain.mapper;

import org.apache.ibatis.annotations.Param;

public interface MqConsumeRecordMapper {

    int exists(@Param("consumerGroup") String consumerGroup,
               @Param("messageKey") String messageKey);

    int insert(@Param("consumerGroup") String consumerGroup,
               @Param("messageKey") String messageKey);
}
