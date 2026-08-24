package hk.ljx.fishhub.comment.biz.kv.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindCommentContentReqDTO {
    private String contentId;
    private String yearMonth;
}
