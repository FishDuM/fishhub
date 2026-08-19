package hk.ljx.fishhub.count.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记计数变更事件 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CountNoteMqDTO {

    private Long userId;

    private Long noteId;

    /**
     * 操作类型
     */
    private Integer type;

    private LocalDateTime createTime;

    /**
     * 笔记发布者 ID
     */
    private Long noteCreatorId;
}
