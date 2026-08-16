package hk.ljx.fishhub.note.biz.service;

import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞/收藏落库的事务消息本地事务入口。
 * 联合唯一索引判定本条关系是否首次生效：未生效（重复消费）则不登记 journal，
 * 事务消息随之回滚丢弃，计数事件与落库事实保持一一对应。
 */
@Service
public class NoteInteractionPersistenceService {

    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Resource
    private TxJournalStore txJournalStore;

    @Transactional(rollbackFor = Exception.class)
    public boolean saveLike(NoteLikeDO noteLike, String txId) {
        if (noteLikeDOMapper.insertOrUpdate(noteLike) == 0) {
            return false;
        }
        txJournalStore.record(txId);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveUnlike(NoteLikeDO noteLike, String txId) {
        if (noteLikeDOMapper.update2UnlikeByUserIdAndNoteId(noteLike) == 0) {
            return false;
        }
        txJournalStore.record(txId);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveCollect(NoteCollectionDO collection, String txId) {
        if (noteCollectionDOMapper.insertOrUpdate(collection) == 0) {
            return false;
        }
        txJournalStore.record(txId);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveUncollect(NoteCollectionDO collection, String txId) {
        if (noteCollectionDOMapper.update2UnCollectByUserIdAndNoteId(collection) == 0) {
            return false;
        }
        txJournalStore.record(txId);
        return true;
    }
}
