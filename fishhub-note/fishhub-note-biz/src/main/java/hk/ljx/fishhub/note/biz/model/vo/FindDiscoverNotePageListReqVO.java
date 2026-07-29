package hk.ljx.fishhub.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindDiscoverNotePageListReqVO {

    /**
     * 频道 ID
     */
    private Long channelId;

    @NotNull(message = "页码不能为空")
    private Integer pageNo = 1;

}

