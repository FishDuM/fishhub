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
import hk.ljx.fishhub.comment.biz.model.bo.CommentFirstReplyBO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentItemMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 消费评论发布事件，为首次收到回复的一级评论回填 first_reply_comment_id。
 * Redis haveFirstReply 标记 + 数据库事实双重判定，重复投递安全；批量回填减少单条 UPDATE。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED + "_first_reply_comment_id",
        topic = MQConstants.TOPIC_COMMENT_CHANGED)
@Slf4j
public class CommentChangedFirstReplyUpdateConsumer implements RocketMQListener<String> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
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

        List<Long> parentIds = event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CommentItemMqDTO::getParentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(parentIds)) {
            return;
        }

        // 过滤 Redis 中已标记拥有首条回复的一级评论
        List<String> keys = parentIds.stream()
                .map(RedisKeyConstants::buildHaveFirstReplyCommentKey)
                .toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
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

        // 既有首回复的一级评论直接同步 Redis 标记
        List<Long> alreadyHasReplyIds = commentDOS.stream()
                .filter(commentDO -> commentDO.getFirstReplyCommentId() != 0)
                .map(CommentDO::getId)
                .toList();
        if (CollUtil.isNotEmpty(alreadyHasReplyIds)) {
            threadPoolTaskExecutor.submit(() -> {
                try {
                    sync2Redis(alreadyHasReplyIds);
                } catch (Exception e) {
                    log.warn("Redis 不可用，评论首回复缓存同步失败", e);
                }
            });
        }

        // 尚未回填的一级评论：一次批量查各自最早回复，再一次批量回填
        List<Long> needUpdateCommentIds = commentDOS.stream()
                .filter(commentDO -> commentDO.getFirstReplyCommentId() == 0)
                .map(CommentDO::getId)
                .toList();
        if (CollUtil.isEmpty(needUpdateCommentIds)) {
            return;
        }

        List<CommentDO> earliestReplies = commentDOMapper.selectEarliestFirstReplyByParentIds(needUpdateCommentIds);
        if (CollUtil.isEmpty(earliestReplies)) {
            return;
        }

        List<CommentFirstReplyBO> replyBOS = earliestReplies.stream()
                .map(reply -> CommentFirstReplyBO.builder()
                        .id(reply.getParentId())
                        .firstReplyCommentId(reply.getId())
                        .build())
                .toList();
        commentDOMapper.batchUpdateFirstReplyCommentIds(replyBOS);

        List<Long> updatedCommentIds = replyBOS.stream().map(CommentFirstReplyBO::getId).toList();
        threadPoolTaskExecutor.submit(() -> {
            try {
                sync2Redis(updatedCommentIds);
            } catch (Exception e) {
                log.warn("Redis 不可用，评论首回复缓存同步失败", e);
            }
        });
    }

    /**
     * 同步 haveFirstReply 标记并失效该评论的远端与本地详情缓存
     */
    private void sync2Redis(List<Long> needSyncCommentIds) {
        needSyncCommentIds.forEach(commentId -> {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.buildHaveFirstReplyCommentKey(commentId),
                    "1",
                    RandomUtil.randomInt(1, 5 * 60 * 60),
                    TimeUnit.SECONDS);
            stringRedisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(commentId));
            try {
                rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, String.valueOf(commentId));
            } catch (Exception e) {
                log.warn("评论本地缓存失效消息发送失败, commentId={}", commentId, e);
            }
        });
    }
}
