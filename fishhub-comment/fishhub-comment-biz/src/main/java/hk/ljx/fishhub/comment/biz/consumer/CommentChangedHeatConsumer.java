package hk.ljx.fishhub.comment.biz.consumer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.bo.CommentHeatBO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentItemMqDTO;
import hk.ljx.fishhub.comment.biz.util.HeatCalculator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消费评论变更事件，按数据库最新值重算相关一级评论的热度。
 * 热度消息可能晚于删除到达：目标评论已不存在时直接确认，重复重算安全。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED + "_heat",
        topic = MQConstants.TOPIC_COMMENT_CHANGED)
@Slf4j
public class CommentChangedHeatConsumer implements RocketMQListener<String> {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(String body) {
        CommentChangedEventMqDTO event = JsonUtils.parseObject(body, CommentChangedEventMqDTO.class);
        if (event == null || event.getChangeType() == null || event.getItems() == null) {
            throw new IllegalArgumentException("评论热度消息格式错误");
        }

        // 二级评论的变动会影响其父评论的热度
        Set<Long> commentIds = Sets.newHashSet();
        event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CommentItemMqDTO::getParentId)
                .filter(Objects::nonNull)
                .forEach(commentIds::add);
        if (commentIds.isEmpty()) {
            return;
        }

        recomputeHeat(commentIds);
    }

    private void recomputeHeat(Set<Long> commentIds) {
        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(commentIds.stream().toList());

        // 目标评论已不存在（已被删除）时直接确认，避免无效更新反复重试。
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

            List<String> args = Lists.newArrayList();
            commentHeatBOS.forEach(commentHeatBO -> {
                args.add(String.valueOf(commentHeatBO.getId()));
                args.add(String.valueOf(commentHeatBO.getHeat()));
            });
            stringRedisTemplate.execute(script, Collections.singletonList(key), args.toArray());
        });
    }
}
