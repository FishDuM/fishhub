package hk.ljx.fishhub.count.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评论变更统一事件：一个业务事务（发布、删除）只产生一条本事件，
 * 由 count 模块与评论模块自身的多个 consumer group 分别订阅。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentChangedEventMqDTO {

    /**
     * 变更类型：1 - 发布；0 - 删除
     */
    private Integer changeType;

    /**
     * 本次变更涉及的评论（发布为新增集合，删除为被删集合）
     */
    private List<CommentItemMqDTO> items;
}
