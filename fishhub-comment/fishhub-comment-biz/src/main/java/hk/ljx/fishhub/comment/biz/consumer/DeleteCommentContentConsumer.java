package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.model.dto.DeleteCommentContentMqDTO;
import hk.ljx.fishhub.comment.biz.rpc.KeyValueRpcService;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT_CONTENT,
        topic = MQConstants.TOPIC_DELETE_COMMENT_CONTENT)
public class DeleteCommentContentConsumer implements RocketMQListener<String> {

    @Resource
    private KeyValueRpcService keyValueRpcService;

    @Override
    public void onMessage(String body) {
        DeleteCommentContentMqDTO task;
        try {
            task = JsonUtils.parseObject(body, DeleteCommentContentMqDTO.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("评论正文删除消息格式错误", e);
        }
        if (task == null || task.getNoteId() == null || task.getCreateTime() == null || task.getContentUuid() == null) {
            throw new IllegalArgumentException("评论正文删除消息格式错误");
        }
        try {
            keyValueRpcService.deleteCommentContent(task.getNoteId(), task.getCreateTime(), task.getContentUuid());
        } catch (Exception e) {
            throw new IllegalStateException("删除评论正文失败", e);
        }
    }
}
