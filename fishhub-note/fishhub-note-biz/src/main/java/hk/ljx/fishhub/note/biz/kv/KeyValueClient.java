package hk.ljx.fishhub.note.biz.kv;

import hk.ljx.fishhub.note.biz.domain.dataobject.NoteContentDO;
import hk.ljx.fishhub.note.biz.domain.repository.NoteContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 笔记正文存储本地组件（直连 Cassandra，消除跨进程 RPC 开销）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KeyValueClient {

    private final NoteContentRepository noteContentRepository;

    /**
     * 保存笔记内容
     */
    public boolean saveNoteContent(String uuid, String content) {
        try {
            NoteContentDO noteContentDO = NoteContentDO.builder()
                    .id(UUID.fromString(uuid))
                    .content(content)
                    .build();
            noteContentRepository.save(noteContentDO);
            return true;
        } catch (Exception e) {
            log.error("Cassandra 保存笔记正文异常, uuid={}", uuid, e);
            return false;
        }
    }

    /**
     * 删除笔记内容
     */
    public boolean deleteNoteContent(String uuid) {
        try {
            noteContentRepository.deleteById(UUID.fromString(uuid));
            return true;
        } catch (Exception e) {
            log.error("Cassandra 删除笔记正文异常, uuid={}", uuid, e);
            return false;
        }
    }

    /**
     * 查询笔记内容
     */
    public String findNoteContent(String uuid) {
        try {
            return noteContentRepository.findById(UUID.fromString(uuid))
                    .map(NoteContentDO::getContent)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Cassandra 查询笔记正文异常, uuid={}", uuid, e);
            return null;
        }
    }
}
