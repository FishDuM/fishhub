package hk.ljx.fishhub.comment.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@PrimaryKeyClass
public class CommentContentPrimaryKey implements Serializable {

    @PrimaryKeyColumn(name = "note_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    private Long noteId;

    @PrimaryKeyColumn(name = "year_month", type = PrimaryKeyType.PARTITIONED, ordinal = 1)
    private String yearMonth;

    @PrimaryKeyColumn(name = "content_id", type = PrimaryKeyType.CLUSTERED, ordinal = 2)
    private UUID contentId;
}
