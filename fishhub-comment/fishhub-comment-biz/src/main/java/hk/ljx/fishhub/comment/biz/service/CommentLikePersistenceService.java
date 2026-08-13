package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.comment.biz.retry.SendMqRetryHelper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CommentLikePersistenceService {

    @Resource
    private CommentLikeDOMapper commentLikeDOMapper;
    @Resource
    private SendMqRetryHelper sendMqRetryHelper;

    @Transactional(rollbackFor = Exception.class)
    public boolean apply(LikeUnlikeCommentMqDTO operation, String eventBody) {
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
        sendMqRetryHelper.enqueue(MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE, eventBody);
        return true;
    }
}
