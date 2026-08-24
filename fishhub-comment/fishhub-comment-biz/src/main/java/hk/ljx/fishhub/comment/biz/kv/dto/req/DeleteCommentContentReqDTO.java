package hk.ljx.fishhub.comment.biz.kv.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeleteCommentContentReqDTO {
    private String contentId;
    private Long noteId;
    private String yearMonth;
}
