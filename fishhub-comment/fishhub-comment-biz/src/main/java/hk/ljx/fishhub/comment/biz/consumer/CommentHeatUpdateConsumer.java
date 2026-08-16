package hk.ljx.fishhub.comment.biz.consumer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.model.bo.CommentHeatBO;
import hk.ljx.fishhub.comment.biz.util.HeatCalculator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消费评论点赞聚合落库后的热度重算事件（Set<评论 ID>）。
 * 热度按数据库最新值重算，重复投递安全；目标评论已不存在时直接确认。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_HEAT_UPDATE,
        topic = MQConstants.TOPIC_COMMENT_HEAT_UPDATE)
@Slf4j
public class CommentHeatUpdateConsumer implements RocketMQListener<String> {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        Set<Long> commentIds = Sets.newHashSet();
        try {
            commentIds.addAll(JsonUtils.parseSet(body, Long.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("评论热度消息格式错误", e);
        }
        if (commentIds.isEmpty()) {
            return;
        }
        recomputeHeat(commentIds);
    }

    private void recomputeHeat(Set<Long> commentIds) {
        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(commentIds.stream().toList());

        // 热度消息可能晚于删评论消息到达。目标评论已不存在时直接确认消费。
        if (commentDOS == null || commentDOS.isEmpty()) {
            log.info("==> 评论已不存在，忽略本次热度更新, commentIds: {}", commentIds);
            return;
        }

        List<Long> ids = Lists.newArrayList();
        List<CommentHeatBO> commentBOS = Lists.newArrayList();
        commentDOS.forEach(commentDO -> {
            BigDecimal heatNum = HeatCalculator.calculateHeat(
                    commentDO.getLikeTotal(), commentDO.getChildCommentTotal());
            ids.add(commentDO.getId());
            commentBOS.add(CommentHeatBO.builder()
                    .id(commentDO.getId())
                    .heat(heatNum.doubleValue())
                    .noteId(commentDO.getNoteId())
                    .build());
        });

        int count = commentDOMapper.batchUpdateHeatByCommentIds(ids, commentBOS);
        if (count == 0) {
            return;
        }
        updateRedisHotComments(commentBOS);
    }

    /**
     * 更新 Redis 中热点评论 ZSET
     */
    private void updateRedisHotComments(List<CommentHeatBO> commentHeatBOList) {
        Map<Long, List<CommentHeatBO>> noteIdAndBOListMap = commentHeatBOList.stream()
                .filter(commentHeatBO -> commentHeatBO.getHeat() > 0)
                .collect(Collectors.groupingBy(CommentHeatBO::getNoteId));

        noteIdAndBOListMap.forEach((noteId, commentHeatBOS) -> {
            String key = RedisKeyConstants.buildCommentListKey(noteId);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/update_hot_comments.lua")));
            script.setResultType(Long.class);

            List<Object> args = Lists.newArrayList();
            commentHeatBOS.forEach(commentHeatBO -> {
                args.add(commentHeatBO.getId());
                args.add(commentHeatBO.getHeat());
            });
            redisTemplate.execute(script, Collections.singletonList(key), args.toArray());
        });
    }
}
