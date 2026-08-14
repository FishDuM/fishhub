package hk.ljx.fishhub.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteActionListReqVO {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /**
     * 上一页最后一条互动记录的操作时间。
     */
    private LocalDateTime cursorTime;

    /**
     * 上一页最后一条互动记录的 ID，与操作时间组成稳定游标。
     */
    private Long cursorId;
}
