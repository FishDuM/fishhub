package hk.ljx.fishhub.note.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteContentTaskMqDTO {

    /**
     * 正文所属笔记。消费者据此确认写入任务仍属于当前笔记快照。
     */
    private Long noteId;

    private String contentUuid;

    private String content;

    private String type;
}
