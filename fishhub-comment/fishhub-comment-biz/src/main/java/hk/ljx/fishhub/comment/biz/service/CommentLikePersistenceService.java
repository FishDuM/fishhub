package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 评论点赞关系落库的事务消息本地事务入口。
 * 关系未实际变化（重复消费）则不登记 journal，半消息随之回滚丢弃。
 */
@Service
public class CommentLikePersistenceService {

    @Resource
    private CommentLikeDOMapper commentLikeDOMapper;
    @Resource
    private TxJournalStore txJournalStore;

    @Transactional(rollbackFor = Exception.class)
    public boolean apply(LikeUnlikeCommentMqDTO operation, String txId) {
        int affectedRows;
        if (Objects.equals(operation.getType(), LikeUnlikeCommentTypeEnum.LIKE.getCode())) {
            affectedRows = commentLikeDOMapper.insertIfAbsent(operation);
        } else if (Objects.equals(operation.getType(), LikeUnlikeCommentTypeEnum.UNLIKE.getCode())) {
            affectedRows = commentLikeDOMapper.deleteIfPresent(operation);
        } else {
            throw new IllegalArgumentException("未知的评论点赞操作类型: " + operation.getType());
        }

        if (affectedRows == 0) {
            return false;
        }
        txJournalStore.record(txId);
        return true;
    }
}
