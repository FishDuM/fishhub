package hk.ljx.fishhub.note.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CollectUnCollectNoteMqDTO {

    /**
     * 事件唯一 ID（源头生成，消费/重投链路透传，用于计数事件级幂等）
     */
    private String eventId;

    private Long userId;

    private Long noteId;

    /**
     * 0: 取消收藏， 1：收藏
     */
    private Integer type;

    private LocalDateTime createTime;

    /**
     * 笔记发布者 ID
     */
    private Long noteCreatorId;
}
