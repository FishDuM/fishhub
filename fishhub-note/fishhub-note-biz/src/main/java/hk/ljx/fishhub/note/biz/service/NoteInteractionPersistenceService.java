package hk.ljx.fishhub.note.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.framework.mq.idempotent.MqConsumeRecordStore;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 点赞/收藏批量落库的事务消息本地事务入口；批级 consume_record 判重，重复投递整批跳过。 */
@Service
@RequiredArgsConstructor
public class NoteInteractionPersistenceService {

    private final NoteLikeDOMapper noteLikeDOMapper;
    private final NoteCollectionDOMapper noteCollectionDOMapper;
    private final MqConsumeRecordStore mqConsumeRecordStore;
    private final TxJournalStore txJournalStore;

    @Transactional(rollbackFor = Exception.class)
    public boolean saveNoteLikeBatch(List<NoteLikeDO> noteLikes, String consumeGroup, String batchKey, String txId) {
        if (CollUtil.isEmpty(noteLikes)) {
            return false;
        }
        try {
            mqConsumeRecordStore.insert(consumeGroup, batchKey);
        } catch (DuplicateKeyException e) {
            return false;
        }
        noteLikeDOMapper.insertOrUpdateBatch(noteLikes);
        txJournalStore.record(txId);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveNoteCollectBatch(List<NoteCollectionDO> noteCollections, String consumeGroup, String batchKey, String txId) {
        if (CollUtil.isEmpty(noteCollections)) {
            return false;
        }
        try {
            mqConsumeRecordStore.insert(consumeGroup, batchKey);
        } catch (DuplicateKeyException e) {
            return false;
        }
        noteCollectionDOMapper.insertOrUpdateBatch(noteCollections);
        txJournalStore.record(txId);
        return true;
    }
}
