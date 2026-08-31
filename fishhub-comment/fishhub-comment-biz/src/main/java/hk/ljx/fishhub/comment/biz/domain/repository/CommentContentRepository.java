package hk.ljx.fishhub.comment.biz.domain.repository;

import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentDO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentPrimaryKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentContentRepository extends CassandraRepository<CommentContentDO, CommentContentPrimaryKey> {

    @Query("SELECT * FROM comment_content WHERE note_id = ?0 AND year_month = ?1 AND content_id IN ?2")
    List<CommentContentDO> findByNoteIdAndYearMonthAndContentIdIn(Long noteId, String yearMonth, List<UUID> contentIds);
}
