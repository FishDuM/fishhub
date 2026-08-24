package hk.ljx.fishhub.note.biz.domain.repository;

import hk.ljx.fishhub.note.biz.domain.dataobject.NoteContentDO;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NoteContentRepository extends CassandraRepository<NoteContentDO, UUID> {
}
