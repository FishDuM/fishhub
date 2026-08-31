package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentPrimaryKey;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.repository.CommentContentRepository;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentChangedContentSyncConsumerTest {

    @Mock
    private CommentContentRepository commentContentRepository;
    @Mock
    private CommentDOMapper commentDOMapper;
    @InjectMocks
    private CommentChangedContentSyncConsumer consumer;

    private static final String UUID_TEXT = "a0000000-0000-0000-0000-000000000001";

    @Test
    void shouldSkipImageOnlyCommentAndSyncTextCommentInSameBatch() {
        CommentItemMqDTO imageItem = item(1L, true, null, null);
        CommentItemMqDTO textItem = item(2L, false, UUID_TEXT, "hello");
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(List.of(
                CommentDO.builder().id(2L).contentUuid(UUID_TEXT).build()));

        consumer.onMessage(body(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH, List.of(imageItem, textItem)));

        verify(commentContentRepository).saveAll(anyList());
        verify(commentContentRepository, never()).deleteById(any(CommentContentPrimaryKey.class));
    }

    @Test
    void shouldCleanStaleTextCommentInsteadOfSaving() {
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(List.of());

        consumer.onMessage(body(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH,
                List.of(item(2L, false, UUID_TEXT, "hello"))));

        verify(commentContentRepository).deleteById(any(CommentContentPrimaryKey.class));
        verify(commentContentRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldSkipImageOnlyCommentInDeleteEvent() {
        CommentItemMqDTO imageItem = item(1L, true, null, null);
        CommentItemMqDTO textItem = item(2L, false, UUID_TEXT, null);

        consumer.onMessage(body(MQConstants.COMMENT_CHANGE_TYPE_DELETE, List.of(imageItem, textItem)));

        verify(commentContentRepository).deleteById(any(CommentContentPrimaryKey.class));
        verify(commentContentRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldRejectNonEmptyItemWithoutContentUuid() {
        CommentItemMqDTO brokenItem = item(1L, false, null, "hello");

        assertThrows(IllegalArgumentException.class, () -> consumer.onMessage(
                body(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH, List.of(brokenItem))));
    }

    private String body(Integer changeType, List<CommentItemMqDTO> items) {
        return JsonUtils.toJsonString(CommentChangedEventMqDTO.builder()
                .changeType(changeType)
                .items(items)
                .build());
    }

    private CommentItemMqDTO item(Long id, boolean contentEmpty, String contentUuid, String content) {
        return CommentItemMqDTO.builder()
                .id(id)
                .noteId(10L)
                .level(1)
                .parentId(10L)
                .userId(100L)
                .contentUuid(contentUuid)
                .content(content)
                .isContentEmpty(contentEmpty)
                .createTime(LocalDateTime.of(2026, 8, 16, 12, 0))
                .build();
    }
}
