package hk.ljx.fishhub.comment.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCommentContentMqDTO {

    private Long noteId;
    private LocalDateTime createTime;
    private String contentUuid;
}
