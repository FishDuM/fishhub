package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.retry.ReliableMqOutbox;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotePersistenceService {

    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private ReliableMqOutbox reliableMqOutbox;

    @Transactional(rollbackFor = Exception.class)
    public void savePublishedNote(NoteDO note, String eventDestination, String eventBody, String contentTaskBody) {
        noteDOMapper.insert(note);
        reliableMqOutbox.enqueue(eventDestination, eventBody);
        reliableMqOutbox.enqueue(MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE, eventBody);
        if (contentTaskBody != null) {
            reliableMqOutbox.enqueue(MQConstants.TOPIC_SYNC_NOTE_CONTENT, contentTaskBody);
        }
    }
}
