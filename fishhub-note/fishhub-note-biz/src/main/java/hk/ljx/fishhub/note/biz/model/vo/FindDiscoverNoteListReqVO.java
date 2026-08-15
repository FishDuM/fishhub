package hk.ljx.fishhub.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class FindDiscoverNoteListReqVO {
    private Long channelId;

    /**
     * 游标分页：首屏传 0，后续传上一页最后一条笔记 ID。
     */
    @NotNull(message = "游标不能为空")
    @PositiveOrZero(message = "游标不能小于 0")
    private Long cursor;
}
