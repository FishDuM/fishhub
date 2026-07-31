package hk.ljx.fishhub.count.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AggregationCountLikeUnlikeCommentMqDTO {

    /**
     * 评论 ID
     */
    private Long commentId;

    /**
     * 聚合后的计数
     */
    private Integer count;

}
