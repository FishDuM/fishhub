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


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_HEAT_UPDATE, // Group 组
        topic = MQConstants.TOPIC_COMMENT_HEAT_UPDATE // 主题 Topic
        )
@Slf4j
public class CommentHeatUpdateConsumer implements RocketMQListener<String> {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        consumeMessage(List.of(body));
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【评论热度值计算】聚合消息, size: {}", bodys.size());
        log.info("==> 【评论热度值计算】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // 将聚合后的消息体 Json 转 Set<Long>, 去重相同的评论 ID, 防止重复计算
        Set<Long> commentIds = Sets.newHashSet();
        for (String body : bodys) {
            try {
                commentIds.addAll(JsonUtils.parseSet(body, Long.class));
            } catch (Exception e) {
                throw new IllegalArgumentException("评论热度消息格式错误", e);
            }
        }

        if (commentIds.isEmpty()) {
            return;
        }

        log.info("==> 去重后的评论 ID: {}", commentIds);

        // 批量查询评论
        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(commentIds.stream().toList());

        // 热度消息可能晚于删评论消息到达。目标评论已不存在时直接确认消费，
        // 避免生成没有 WHEN 和 IN 参数的无效批量更新 SQL 并反复重试。
        if (commentDOS == null || commentDOS.isEmpty()) {
            log.info("==> 评论已不存在，忽略本次热度更新, commentIds: {}", commentIds);
            return;
        }

        // 评论 ID
        List<Long> ids = Lists.newArrayList();
        // 热度值 BO
        List<CommentHeatBO> commentBOS = Lists.newArrayList();

        // 重新计算每条评论的热度值
        commentDOS.forEach(commentDO -> {
            Long commentId = commentDO.getId();
            // 被点赞数
            Long likeTotal = commentDO.getLikeTotal();
            // 被回复数
            Long childCommentTotal = commentDO.getChildCommentTotal();

            // 计算热度值
            BigDecimal heatNum = HeatCalculator.calculateHeat(likeTotal, childCommentTotal);
            ids.add(commentId);
            commentBOS.add(CommentHeatBO.builder()
                    .id(commentId)
                    .heat(heatNum.doubleValue())
                    .noteId(commentDO.getNoteId())
                    .build());
        });

        // 批量更新评论热度值
        int count = commentDOMapper.batchUpdateHeatByCommentIds(ids, commentBOS);

        if (count == 0) return;

        // 更新 Redis 中热度评论 ZSET
        updateRedisHotComments(commentBOS);
    }

    /**
     * 更新 Redis 中热点评论 ZSET
     *
     * @param commentHeatBOList
     */
    private void updateRedisHotComments(List<CommentHeatBO> commentHeatBOList) {
        // 过滤出热度值大于 0 的，并按所属笔记 ID 分组（若热度等于0，则不进行更新）
        Map<Long, List<CommentHeatBO>> noteIdAndBOListMap = commentHeatBOList.stream()
                .filter(commentHeatBO -> commentHeatBO.getHeat() > 0)
                .collect(Collectors.groupingBy(CommentHeatBO::getNoteId));

        // 循环
        noteIdAndBOListMap.forEach((noteId, commentHeatBOS) -> {
            // 构建热点评论 Redis Key
            String key = RedisKeyConstants.buildCommentListKey(noteId);

            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            // Lua 脚本路径
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/update_hot_comments.lua")));
            // 返回值类型
            script.setResultType(Long.class);

            // 构建执行 Lua 脚本所需的 ARGS 参数
            List<Object> args = Lists.newArrayList();
            commentHeatBOS.forEach(commentHeatBO -> {
                args.add(commentHeatBO.getId()); // Member: 评论ID
                args.add(commentHeatBO.getHeat()); // Score: 热度值
            });

            // 执行 Lua 脚本
            redisTemplate.execute(script, Collections.singletonList(key), args.toArray());
        });
    }
}
