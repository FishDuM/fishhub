package hk.ljx.fishhub.count.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记变更统一事件（count 模块本地副本，与 note 模块生产方契约一致）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteChangedEventMqDTO {

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 笔记发布者 ID
     */
    private Long creatorId;

    /**
     * 变更类型：0 - 删除； 1 - 发布； 2 - 编辑
     */
    private Integer changeType;
}
