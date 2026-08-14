package hk.ljx.fishhub.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteActionListRspVO {

    /**
     * 笔记分页数据。
     */
    private List<NoteItemRspVO> notes;

    /**
     * 下一页互动游标的操作时间。
     */
    private LocalDateTime nextCursorTime;

    /**
     * 下一页互动游标的记录 ID。
     */
    private Long nextCursorId;
}
