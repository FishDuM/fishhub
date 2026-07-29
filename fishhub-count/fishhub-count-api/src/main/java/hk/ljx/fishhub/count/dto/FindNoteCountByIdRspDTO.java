package hk.ljx.fishhub.count.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteCountByIdRspDTO {

    private Long noteId;

    private Long likeTotal;

    private Long collectTotal;

    private Long commentTotal;
}

