package hk.ljx.fishhub.comment.biz.domain.repository;

import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentDO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentPrimaryKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentContentRepository extends CassandraRepository<CommentContentDO, CommentContentPrimaryKey> {
}
