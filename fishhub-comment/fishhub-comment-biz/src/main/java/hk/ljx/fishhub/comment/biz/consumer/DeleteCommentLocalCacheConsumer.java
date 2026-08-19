package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, // Group
        topic = MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, // 消费的主题 Topic
        messageModel = MessageModel.BROADCASTING) // 广播模式
@RequiredArgsConstructor
public class DeleteCommentLocalCacheConsumer implements RocketMQListener<String>  {

    private final CommentService commentService;

    @Override
    public void onMessage(String body) {
        Long commentId = Long.valueOf(body);
        log.info("## 消费者消费成功, commentId: {}", commentId);

        commentService.deleteCommentLocalCache(commentId);
    }
}
