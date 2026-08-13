package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.comment.biz.model.dto.SyncCommentContentMqDTO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.rpc.KeyValueRpcService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_SYNC_COMMENT_CONTENT,
        topic = MQConstants.TOPIC_SYNC_COMMENT_CONTENT)
public class SyncCommentContentConsumer implements RocketMQListener<String> {

    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private CommentDOMapper commentDOMapper;

    @Override
    public void onMessage(String body) {
        SyncCommentContentMqDTO task = JsonUtils.parseObject(body, SyncCommentContentMqDTO.class);
        if (task == null || task.getCommentId() == null || task.getNoteId() == null || task.getCreateTime() == null
                || StringUtils.isBlank(task.getContentUuid()) || StringUtils.isBlank(task.getContent())) {
            throw new IllegalArgumentException("评论正文同步消息缺少必要字段");
        }
        CommentDO current = commentDOMapper.selectByPrimaryKey(task.getCommentId());
        // 评论被删除或正文标识已变化时，旧任务不再具有写入资格，直接确认即可。
        if (!matchesCurrentContent(current, task)) {
            keyValueRpcService.deleteCommentContent(task.getNoteId(), task.getCreateTime(), task.getContentUuid());
            return;
        }
        try {
            keyValueRpcService.batchSaveCommentContent(List.of(CommentBO.builder()
                    .noteId(task.getNoteId())
                    .createTime(task.getCreateTime())
                    .contentUuid(task.getContentUuid())
                    .content(task.getContent())
                    .build()));
            // 删除可能发生在写前校验之后；若任务已过期，清理刚写入的旧正文。
            if (!matchesCurrentContent(commentDOMapper.selectByPrimaryKey(task.getCommentId()), task)) {
                keyValueRpcService.deleteCommentContent(task.getNoteId(), task.getCreateTime(), task.getContentUuid());
            }
        } catch (Exception e) {
            throw new IllegalStateException("评论正文同步到 KV 失败", e);
        }
    }

    private boolean matchesCurrentContent(CommentDO comment, SyncCommentContentMqDTO task) {
        return comment != null && StringUtils.equals(comment.getContentUuid(), task.getContentUuid());
    }
}
