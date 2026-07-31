package hk.ljx.fishhub.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FindDiscoverNoteListReqVO {
    private Long channelId;

    @NotNull(message = "页码不能为空")
    private Integer pageNo;
}
