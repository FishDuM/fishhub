package hk.ljx.fishhub.note.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FindTopicListReqVO {
    @NotBlank(message = "话题关键词不能为空")
    private String keyword;
}
