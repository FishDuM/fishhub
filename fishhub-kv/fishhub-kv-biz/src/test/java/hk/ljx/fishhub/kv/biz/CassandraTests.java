package hk.ljx.fishhub.kv.biz;

import hk.ljx.fishhub.kv.biz.domain.dataobject.NoteContentDO;
import hk.ljx.fishhub.kv.biz.domain.repository.NoteContentRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FISHHUB_RUN_INTEGRATION_TESTS", matches = "true")
class CassandraTests {

    @Resource
    private NoteContentRepository noteContentRepository;

    @Test
    void noteContentRoundTrip() {
        UUID id = UUID.randomUUID();
        try {
            noteContentRepository.save(new NoteContentDO(id, "初始内容"));
            assertEquals("初始内容", noteContentRepository.findById(id).orElseThrow().getContent());

            noteContentRepository.save(new NoteContentDO(id, "更新后的内容"));
            assertEquals("更新后的内容", noteContentRepository.findById(id).orElseThrow().getContent());
        } finally {
            noteContentRepository.deleteById(id);
        }
        assertFalse(noteContentRepository.existsById(id));
    }
}
