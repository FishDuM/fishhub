package hk.ljx.fishhub.comment.biz.consumer;

import com.google.common.collect.Lists;
import hk.ljx.framework.common.constant.DateConstants;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentDO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentPrimaryKey;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.repository.CommentContentRepository;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消费评论变更事件中的正文任务，幂等同步到 Cassandra。
 * 写前校验 contentUuid 归属 + 写后复核，天然幂等于消息重投递。
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED + "_content_sync",
        topic = MQConstants.TOPIC_COMMENT_CHANGED)
@RequiredArgsConstructor
public class CommentChangedContentSyncConsumer implements RocketMQListener<String> {

    private final CommentContentRepository commentContentRepository;
    private final CommentDOMapper commentDOMapper;

    @Override
    public void onMessage(String body) {
        CommentChangedEventMqDTO event = JsonUtils.parseObject(body, CommentChangedEventMqDTO.class);
        if (event == null || event.getChangeType() == null || event.getItems() == null) {
            throw new IllegalArgumentException("评论变更消息缺少必要字段");
        }
        if (Objects.equals(event.getChangeType(), MQConstants.COMMENT_CHANGE_TYPE_DELETE)) {
            deleteCommentContents(event.getItems());
            return;
        }
        syncCurrentContents(event.getItems());
    }

    private void deleteCommentContents(List<CommentItemMqDTO> items) {
        for (CommentItemMqDTO item : items) {
            validate(item);
            if (Boolean.TRUE.equals(item.getIsContentEmpty())) {
                continue;
            }
            deleteCommentContent(item.getNoteId(), item.getCreateTime(), item.getContentUuid());
        }
    }

    private void syncCurrentContents(List<CommentItemMqDTO> items) {
        List<CommentItemMqDTO> syncItems = Lists.newArrayList();
        for (CommentItemMqDTO item : items) {
            validate(item);
            if (Boolean.TRUE.equals(item.getIsContentEmpty()) || StringUtils.isBlank(item.getContent())) {
                continue;
            }
            syncItems.add(item);
        }
        if (syncItems.isEmpty()) {
            return;
        }

        List<Long> ids = syncItems.stream().map(CommentItemMqDTO::getId).distinct().toList();
        Map<Long, CommentDO> currentById = commentDOMapper.selectByCommentIds(ids).stream()
                .collect(Collectors.toMap(CommentDO::getId, Function.identity(), (left, right) -> left));

        List<CommentBO> toSave = Lists.newArrayList();
        for (CommentItemMqDTO item : syncItems) {
            CommentDO current = currentById.get(item.getId());
            // 评论已删除或正文标识已变化时，旧任务不再具有写入资格，清理旧正文即可。
            if (current == null || !StringUtils.equals(current.getContentUuid(), item.getContentUuid())) {
                deleteCommentContent(item.getNoteId(), item.getCreateTime(), item.getContentUuid());
                continue;
            }
            toSave.add(CommentBO.builder()
                    .id(item.getId())
                    .noteId(item.getNoteId())
                    .createTime(item.getCreateTime())
                    .contentUuid(item.getContentUuid())
                    .content(item.getContent())
                    .build());
        }
        if (toSave.isEmpty()) {
            return;
        }
        try {
            List<CommentContentDO> contentDOS = toSave.stream().map(bo -> CommentContentDO.builder()
                    .primaryKey(CommentContentPrimaryKey.builder()
                            .noteId(bo.getNoteId())
                            .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(bo.getCreateTime()))
                            .contentId(UUID.fromString(bo.getContentUuid()))
                            .build())
                    .content(bo.getContent())
                    .build()).toList();
            commentContentRepository.saveAll(contentDOS);
        } catch (Exception e) {
            log.error("Cassandra 批量保存评论内容异常", e);
            throw new IllegalStateException("评论正文同步到 KV 失败", e);
        }

        // 删除可能发生在写前校验之后；若任务已过期，清理刚写入的旧正文。
        Map<Long, CommentDO> afterById = commentDOMapper.selectByCommentIds(
                toSave.stream().map(CommentBO::getId).distinct().toList()).stream()
                .collect(Collectors.toMap(CommentDO::getId, Function.identity(), (left, right) -> left));
        toSave.stream()
                .filter(bo -> {
                    CommentDO after = afterById.get(bo.getId());
                    return after == null || !StringUtils.equals(after.getContentUuid(), bo.getContentUuid());
                })
                .forEach(bo -> deleteCommentContent(bo.getNoteId(), bo.getCreateTime(), bo.getContentUuid()));
    }

    private void deleteCommentContent(Long noteId, LocalDateTime createTime, String contentUuid) {
        try {
            CommentContentPrimaryKey primaryKey = CommentContentPrimaryKey.builder()
                    .noteId(noteId)
                    .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(createTime))
                    .contentId(UUID.fromString(contentUuid))
                    .build();
            commentContentRepository.deleteById(primaryKey);
        } catch (Exception e) {
            log.error("Cassandra 删除评论内容异常, noteId={}, contentUuid={}", noteId, contentUuid, e);
        }
    }

    private void validate(CommentItemMqDTO item) {
        if (item == null || item.getId() == null || item.getNoteId() == null || item.getCreateTime() == null) {
            throw new IllegalArgumentException("评论变更消息缺少必要字段");
        }
        if (Boolean.TRUE.equals(item.getIsContentEmpty()) || StringUtils.isNotBlank(item.getContentUuid())) {
            return;
        }
        throw new IllegalArgumentException("非空内容评论缺少正文标识");
    }
}
