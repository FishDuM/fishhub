package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import hk.ljx.fishhub.note.biz.retry.ReliableMqOutbox;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteInteractionPersistenceService {

    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Resource
    private ReliableMqOutbox reliableMqOutbox;

    @Transactional(rollbackFor = Exception.class)
    public boolean saveLike(NoteLikeDO noteLike, String eventBody) {
        if (noteLikeDOMapper.insertOrUpdate(noteLike) == 0) {
            return false;
        }
        reliableMqOutbox.enqueue(MQConstants.TOPIC_COUNT_NOTE_LIKE, eventBody);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveUnlike(NoteLikeDO noteLike, String eventBody) {
        if (noteLikeDOMapper.update2UnlikeByUserIdAndNoteId(noteLike) == 0) {
            return false;
        }
        reliableMqOutbox.enqueue(MQConstants.TOPIC_COUNT_NOTE_LIKE, eventBody);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveCollect(NoteCollectionDO collection, String eventBody) {
        if (noteCollectionDOMapper.insertOrUpdate(collection) == 0) {
            return false;
        }
        reliableMqOutbox.enqueue(MQConstants.TOPIC_COUNT_NOTE_COLLECT, eventBody);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveUncollect(NoteCollectionDO collection, String eventBody) {
        if (noteCollectionDOMapper.update2UnCollectByUserIdAndNoteId(collection) == 0) {
            return false;
        }
        reliableMqOutbox.enqueue(MQConstants.TOPIC_COUNT_NOTE_COLLECT, eventBody);
        return true;
    }
}
