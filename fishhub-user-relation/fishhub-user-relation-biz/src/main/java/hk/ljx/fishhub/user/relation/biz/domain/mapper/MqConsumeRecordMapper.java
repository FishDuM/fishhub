package hk.ljx.fishhub.user.relation.biz.domain.mapper;

import org.apache.ibatis.annotations.Param;

public interface MqConsumeRecordMapper {

    int insert(@Param("consumerGroup") String consumerGroup,
               @Param("messageKey") String messageKey);
}
