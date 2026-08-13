package hk.ljx.fishhub.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    private String title;

    @Size(max = 65535, message = "笔记正文不能超过 64KB")
    private String content;

    @NotNull(message = "频道不能为空")
    private Long channelId;

    private Long topicId;

}
