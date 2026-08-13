package hk.ljx.fishhub.comment.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqSendFailureDO {

    private Long id;
    private String messageKey;
    private String topic;
    private String body;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private Integer status;
    private LocalDateTime lockedAt;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
