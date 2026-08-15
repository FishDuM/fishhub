package hk.ljx.fishhub.note.biz.model.vo;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindTopicRspVO {
    private Long id;
    private String name;
}
