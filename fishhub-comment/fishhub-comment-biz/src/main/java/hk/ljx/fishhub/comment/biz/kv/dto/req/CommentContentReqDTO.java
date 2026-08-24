package hk.ljx.fishhub.comment.biz.kv.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentContentReqDTO {
    private String contentId;
    private String content;
    private Long noteId;
    private String yearMonth;
}
