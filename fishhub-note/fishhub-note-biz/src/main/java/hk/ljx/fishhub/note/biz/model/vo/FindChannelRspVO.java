package hk.ljx.fishhub.note.biz.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FindChannelRspVO {
    private Long id;
    private String name;
}
