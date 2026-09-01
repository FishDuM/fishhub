package hk.ljx.fishhub.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import hk.ljx.fishhub.note.biz.enums.NoteUpdateOperationEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateNoteReqVO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long id;

    @NotNull(message = "笔记类型不能为空")
    private Integer type;

    private List<String> imgUris;

    private String videoUri;

    @NotBlank(message = "笔记标题不能为空")
    private String title;

    @Size(max = 65535, message = "笔记正文不能超过 64KB")
    private String content;

    @NotNull(message = "频道不能为空")
    private Long channelId;

    private Long topicId;

    @NotNull(message = "正文更新方式不能为空")
    private NoteUpdateOperationEnum contentOperation;

    @NotNull(message = "话题更新方式不能为空")
    private NoteUpdateOperationEnum topicOperation;

    @NotNull(message = "媒体更新方式不能为空")
    private NoteUpdateOperationEnum mediaOperation;
}
