package hk.ljx.fishhub.comment.biz.service;

import com.google.common.collect.Lists;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.bo.CommentHeatBO;
import hk.ljx.fishhub.comment.biz.util.HeatCalculator;
import hk.ljx.framework.common.util.RedisScriptHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论热度重算公共入口：按数据库最新值重算一级评论热度并回写 DB 与 Redis 热点榜。
 * 由多条事件链（点赞计数落库、评论变更）共用，重复调用安全（脚本按分数 upsert）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommentHeatService {

    private static final DefaultRedisScript<Long> UPDATE_HOT_COMMENTS_SCRIPT = RedisScriptHelper.loadLongScript("/lua/update_hot_comments.lua");

    private final CommentDOMapper commentDOMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 重算指定评论的热度。目标评论已不存在时直接返回；重复重算安全。
     *
     * @param commentIds 一级评论 ID 集合
     */
    public void recomputeHeat(Set<Long> commentIds) {
        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(commentIds.stream().toList());

        // 热度消息可能晚于删评论消息到达。目标评论已不存在时直接确认消费。
        if (commentDOS == null || commentDOS.isEmpty()) {
            log.info("==> 评论已不存在，忽略本次热度更新, commentIds: {}", commentIds);
            return;
        }

        List<CommentDO> levelOneComments = commentDOS.stream()
                .filter(commentDO -> Objects.equals(commentDO.getLevel(), CommentLevelEnum.ONE.getCode()))
                .toList();
        if (levelOneComments.isEmpty()) {
            log.info("==> 热度请求中没有一级评论，忽略本次热度更新, commentIds: {}", commentIds);
            return;
        }

        List<Long> ids = Lists.newArrayList();
        List<CommentHeatBO> commentBOS = Lists.newArrayList();
        levelOneComments.forEach(commentDO -> {
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
            List<String> args = Lists.newArrayList();
            commentHeatBOS.forEach(commentHeatBO -> {
                args.add(String.valueOf(commentHeatBO.getId()));
                args.add(String.valueOf(commentHeatBO.getHeat()));
            });
            stringRedisTemplate.execute(UPDATE_HOT_COMMENTS_SCRIPT, Collections.singletonList(key), args.toArray());
        });
    }
}
