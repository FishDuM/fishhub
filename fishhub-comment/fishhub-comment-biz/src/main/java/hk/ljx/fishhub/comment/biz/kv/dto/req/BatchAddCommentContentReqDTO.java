package hk.ljx.fishhub.comment.biz.kv.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BatchAddCommentContentReqDTO {
    private List<CommentContentReqDTO> comments;
}
