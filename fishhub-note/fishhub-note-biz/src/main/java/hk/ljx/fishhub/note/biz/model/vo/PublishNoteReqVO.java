package hk.ljx.fishhub.note.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublishNoteReqVO {

    @NotNull(message = "笔记类型不能为空")
    private Integer type;

    private List<String> imgUris;

    private String videoUri;

    @NotBlank(message = "笔记标题不能为空")
    private String title;

    private String content;

    /**
     * 已有话题使用 ID，新话题使用名称。
     */
    private List<Object> topics;

    @NotNull(message = "频道不能为空")
    private Long channelId;
}
