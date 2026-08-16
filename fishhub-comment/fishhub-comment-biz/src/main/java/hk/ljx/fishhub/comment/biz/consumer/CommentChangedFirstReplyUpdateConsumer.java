package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentItemMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 消费评论发布事件，为首次收到回复的一级评论回填 first_reply_comment_id。
 * Redis haveFirstReply 标记 + 数据库事实双重判定，重复投递安全。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED + "_first_reply_comment_id",
        topic = MQConstants.TOPIC_COMMENT_CHANGED)
@Slf4j
public class CommentChangedFirstReplyUpdateConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource(name = "fishhubTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        CommentChangedEventMqDTO event = JsonUtils.parseObject(body, CommentChangedEventMqDTO.class);
        if (event == null || event.getChangeType() == null || event.getItems() == null) {
            throw new IllegalArgumentException("一级评论首条回复消息缺少必要字段");
        }
        if (!Objects.equals(event.getChangeType(), MQConstants.COMMENT_CHANGE_TYPE_PUBLISH)) {
            return;
        }

        // 过滤出二级评论的 parent_id（即一级评论 ID），并去重
        List<Long> parentIds = event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CommentItemMqDTO::getParentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (CollUtil.isEmpty(parentIds)) {
            return;
        }

        // 批量查询 Redis 中已标记"拥有首条回复"的一级评论
        List<String> keys = parentIds.stream()
                .map(RedisKeyConstants::buildHaveFirstReplyCommentKey)
                .toList();
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        // 提取 Redis 中不存在的评论 ID，需要进一步核对数据库
        List<Long> missingCommentIds = Lists.newArrayList();
        for (int i = 0; i < values.size(); i++) {
            if (Objects.isNull(values.get(i))) {
                missingCommentIds.add(parentIds.get(i));
            }
        }

        if (CollUtil.isEmpty(missingCommentIds)) {
            return;
        }

        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(missingCommentIds);

        // 异步将 first_reply_comment_id 不为 0 的一级评论 ID 同步到 Redis
        threadPoolTaskExecutor.submit(() -> {
            List<Long> needSyncCommentIds = commentDOS.stream()
                    .filter(commentDO -> commentDO.getFirstReplyCommentId() != 0)
                    .map(CommentDO::getId)
                    .toList();
            sync2Redis(needSyncCommentIds);
        });

        // first_reply_comment_id 仍为 0 的，回填最早回复
        commentDOS.stream()
                .filter(commentDO -> commentDO.getFirstReplyCommentId() == 0)
                .forEach(needUpdateCommentDO -> {
                    Long needUpdateCommentId = needUpdateCommentDO.getId();
                    CommentDO earliestCommentDO = commentDOMapper.selectEarliestByParentId(needUpdateCommentId);
                    if (Objects.nonNull(earliestCommentDO)) {
                        commentDOMapper.updateFirstReplyCommentIdByPrimaryKey(
                                earliestCommentDO.getId(), needUpdateCommentId);
                        threadPoolTaskExecutor.submit(() -> sync2Redis(Lists.newArrayList(needUpdateCommentId)));
                    }
                });
    }

    /**
     * 同步 haveFirstReply 标记并失效该评论的远端与本地详情缓存
     */
    private void sync2Redis(List<Long> needSyncCommentIds) {
        needSyncCommentIds.forEach(commentId -> {
            redisTemplate.opsForValue().set(
                    RedisKeyConstants.buildHaveFirstReplyCommentKey(commentId),
                    1,
                    RandomUtil.randomInt(1, 5 * 60 * 60),
                    TimeUnit.SECONDS);
            redisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(commentId));
            try {
                rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, String.valueOf(commentId));
            } catch (Exception e) {
                // 本地缓存失效尽力而为，节点本地缓存由 TTL 兜底
                log.warn("评论本地缓存失效消息发送失败, commentId={}", commentId, e);
            }
        });
    }
}
