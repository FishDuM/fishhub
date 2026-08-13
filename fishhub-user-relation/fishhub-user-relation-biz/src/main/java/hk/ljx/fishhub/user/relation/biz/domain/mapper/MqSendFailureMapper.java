package hk.ljx.fishhub.user.relation.biz.domain.mapper;

import hk.ljx.fishhub.user.relation.biz.domain.dataobject.MqSendFailureDO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MqSendFailureMapper {

    int insertPending(MqSendFailureDO failure);

    List<MqSendFailureDO> selectRetryable(@Param("now") LocalDateTime now,
                                          @Param("lockExpiredAt") LocalDateTime lockExpiredAt,
                                          @Param("limit") int limit);

    int claim(@Param("id") Long id,
              @Param("now") LocalDateTime now,
              @Param("lockExpiredAt") LocalDateTime lockExpiredAt);

    int deleteById(Long id);

    int deleteByMessageKey(String messageKey);

    int releaseForRetry(@Param("id") Long id,
                        @Param("retryCount") int retryCount,
                        @Param("nextRetryTime") LocalDateTime nextRetryTime,
                        @Param("lastError") String lastError);
}
