package hk.ljx.fishhub.search.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 重建笔记 Elasticsearch 文档的请求参数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RebuildNoteDocumentReqDTO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long id;
}
