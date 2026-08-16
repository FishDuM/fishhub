package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentItemMqDTO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.rpc.KeyValueRpcService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 消费评论变更事件中的正文任务，幂等同步到 KV。
 * 写前校验 contentUuid 归属 + 写后复核，天然幂等于消息重投递。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED + "_content_sync",
        topic = MQConstants.TOPIC_COMMENT_CHANGED)
public class CommentChangedContentSyncConsumer implements RocketMQListener<String> {

    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private CommentDOMapper commentDOMapper;

    @Override
    public void onMessage(String body) {
        CommentChangedEventMqDTO event = JsonUtils.parseObject(body, CommentChangedEventMqDTO.class);
        if (event == null || event.getChangeType() == null || event.getItems() == null) {
            throw new IllegalArgumentException("评论变更消息缺少必要字段");
        }
        for (CommentItemMqDTO item : event.getItems()) {
            if (item == null || item.getId() == null || item.getNoteId() == null
                    || item.getCreateTime() == null) {
                throw new IllegalArgumentException("评论变更消息缺少必要字段");
            }
            // 空内容评论（如纯图片评论）在 KV 中没有正文，没有同步任务，直接跳过。
            if (Boolean.TRUE.equals(item.getIsContentEmpty())) {
                continue;
            }
            if (StringUtils.isBlank(item.getContentUuid())) {
                throw new IllegalArgumentException("非空内容评论缺少正文标识");
            }
            if (Objects.equals(event.getChangeType(), MQConstants.COMMENT_CHANGE_TYPE_DELETE)) {
                keyValueRpcService.deleteCommentContent(item.getNoteId(), item.getCreateTime(), item.getContentUuid());
                continue;
            }
            if (StringUtils.isBlank(item.getContent())) {
                continue;
            }
            syncCurrentContent(item);
        }
    }

    private void syncCurrentContent(CommentItemMqDTO item) {
        CommentDO current = commentDOMapper.selectByPrimaryKey(item.getId());
        // 评论被删除或正文标识已变化时，旧任务不再具有写入资格，直接确认即可。
        if (!matchesCurrentContent(current, item)) {
            keyValueRpcService.deleteCommentContent(item.getNoteId(), item.getCreateTime(), item.getContentUuid());
            return;
        }
        try {
            keyValueRpcService.batchSaveCommentContent(List.of(CommentBO.builder()
                    .noteId(item.getNoteId())
                    .createTime(item.getCreateTime())
                    .contentUuid(item.getContentUuid())
                    .content(item.getContent())
                    .build()));
            // 删除可能发生在写前校验之后；若任务已过期，清理刚写入的旧正文。
            if (!matchesCurrentContent(commentDOMapper.selectByPrimaryKey(item.getId()), item)) {
                keyValueRpcService.deleteCommentContent(item.getNoteId(), item.getCreateTime(), item.getContentUuid());
            }
        } catch (Exception e) {
            throw new IllegalStateException("评论正文同步到 KV 失败", e);
        }
    }

    private boolean matchesCurrentContent(CommentDO comment, CommentItemMqDTO item) {
        return comment != null && StringUtils.equals(comment.getContentUuid(), item.getContentUuid());
    }
}
