package hk.ljx.fishhub.count.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记计数聚合 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AggregationNoteCountMqDTO {

    /**
     * 笔记发布者 ID
     */
    private Long creatorId;

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 聚合计数
     */
    private Integer count;

    /**
     * 批次标识
     */
    private String batchId;
}
