package hk.ljx.fishhub.comment.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvalidateChildCommentListCacheMqDTO {

    private String eventId;

    private Long parentCommentId;
}
