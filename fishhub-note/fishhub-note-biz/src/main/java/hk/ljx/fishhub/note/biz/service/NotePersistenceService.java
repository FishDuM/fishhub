package hk.ljx.fishhub.note.biz.service;

import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.fishhub.note.biz.enums.ResponseCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 笔记元数据事务消息本地事务入口：业务写入与 journal 登记同事务提交，
 * 提交事实由事务消息机制原子地转化为事件可见性。
 */
@Service
@RequiredArgsConstructor
public class NotePersistenceService {

    private final NoteDOMapper noteDOMapper;
    private final TxJournalStore txJournalStore;

    /**
     * 发布笔记（本地单事务写入）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void savePublishedNote(NoteDO note) {
        noteDOMapper.insert(note);
    }

    /**
     * 发布笔记（带事务消息登记）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void savePublishedNote(NoteDO note, String txId) {
        noteDOMapper.insert(note);
        if (txId != null) {
            txJournalStore.record(txId);
        }
    }

    /**
     * 编辑笔记（乐观锁校验版本）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateNote(NoteDO note, String txId) {
        if (noteDOMapper.updateByPrimaryKeyAndRevision(note) != 1) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }
        txJournalStore.record(txId);
    }

    /**
     * 逻辑删除笔记（乐观锁校验版本）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void logicalDeleteNote(NoteDO note, String txId) {
        if (noteDOMapper.logicalDeleteByPrimaryKeyAndRevision(note) != 1) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }
        txJournalStore.record(txId);
    }

    /**
     * 修改笔记可见性。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateNoteVisibility(NoteDO note, String txId) {
        if (noteDOMapper.updateVisibility(note) != 1) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }
        txJournalStore.record(txId);
    }
}
