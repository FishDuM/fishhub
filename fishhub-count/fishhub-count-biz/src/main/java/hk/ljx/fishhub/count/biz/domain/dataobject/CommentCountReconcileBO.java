package hk.ljx.fishhub.count.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCountReconcileBO {
    private Long commentId;
    private Long likeTotal;
    private Long childCommentTotal;
}
