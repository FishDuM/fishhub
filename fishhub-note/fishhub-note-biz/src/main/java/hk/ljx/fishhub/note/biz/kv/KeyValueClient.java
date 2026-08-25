package hk.ljx.fishhub.note.biz.kv;

import hk.ljx.fishhub.note.biz.domain.dataobject.NoteContentDO;
import hk.ljx.fishhub.note.biz.domain.repository.NoteContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 笔记正文存储本地组件
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KeyValueClient {

    private final NoteContentRepository noteContentRepository;

    private UUID parseUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 保存笔记内容
     */
    public boolean saveNoteContent(String uuid, String content) {
        UUID parsedUuid = parseUuid(uuid);
        if (parsedUuid == null) {
            return false;
        }
        try {
            NoteContentDO noteContentDO = NoteContentDO.builder()
                    .id(parsedUuid)
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
        UUID parsedUuid = parseUuid(uuid);
        if (parsedUuid == null) {
            return false;
        }
        try {
            noteContentRepository.deleteById(parsedUuid);
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
        UUID parsedUuid = parseUuid(uuid);
        if (parsedUuid == null) {
            return null;
        }
        try {
            return noteContentRepository.findById(parsedUuid)
                    .map(NoteContentDO::getContent)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Cassandra 查询笔记正文异常, uuid={}", uuid, e);
            return null;
        }
    }
}
