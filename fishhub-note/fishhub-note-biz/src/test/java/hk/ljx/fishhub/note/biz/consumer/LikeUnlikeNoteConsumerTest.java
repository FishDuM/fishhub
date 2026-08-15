package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.LikeUnlikeNoteTypeEnum;
import hk.ljx.fishhub.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionPersistenceService;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeUnlikeNoteConsumerTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private NoteInteractionCacheService noteInteractionCacheService;
    @Mock
    private NoteInteractionPersistenceService persistenceService;
    @InjectMocks
    private LikeUnlikeNoteConsumer consumer;

    @Test
    void shouldRejectLikeOnPrivateNoteAndEvictOptimisticCache() {
        when(noteDOMapper.selectInteractionInfoByNoteId(10L)).thenReturn(
                NoteDO.builder().id(10L).creatorId(99L).visible(1).status(1).build());

        consumer.onMessage(message(MQConstants.TAG_LIKE, LikeUnlikeNoteTypeEnum.LIKE.getCode()));

        verify(noteInteractionCacheService).evictLikeCaches(1L);
        verify(persistenceService, never()).saveLike(any(), any());
    }

    @Test
    void shouldAllowUnlikeToCleanUpPrivateNoteRelation() {
        when(noteDOMapper.selectInteractionInfoByNoteId(10L)).thenReturn(
                NoteDO.builder().id(10L).creatorId(99L).visible(1).status(0).build());
        when(persistenceService.saveUnlike(any(), any())).thenReturn(false);

        consumer.onMessage(message(MQConstants.TAG_UNLIKE, LikeUnlikeNoteTypeEnum.UNLIKE.getCode()));

        verify(persistenceService).saveUnlike(any(), any());
        verify(noteInteractionCacheService, never()).evictLikeCaches(1L);
    }

    private Message message(String tag, Integer type) {
        LikeUnlikeNoteMqDTO dto = LikeUnlikeNoteMqDTO.builder()
                .userId(1L)
                .noteId(10L)
                .type(type)
                .createTime(LocalDateTime.of(2026, 8, 16, 12, 0))
                .build();
        return new Message(MQConstants.TOPIC_LIKE_OR_UNLIKE, tag,
                JsonUtils.toJsonString(dto).getBytes(StandardCharsets.UTF_8));
    }
}
