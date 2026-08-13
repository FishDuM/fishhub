package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.model.dto.NoteOperateMqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvalidateNoteRedisCacheConsumerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @InjectMocks
    private InvalidateNoteRedisCacheConsumer consumer;

    @Test
    void shouldInvalidateDetailAndPublishedListTogether() {
        String body = JsonUtils.toJsonString(NoteOperateMqDTO.builder()
                .noteId(10L)
                .creatorId(20L)
                .build());

        consumer.onMessage(body);

        verify(redisTemplate).delete(List.of(
                RedisKeyConstants.buildNoteDetailKey(10L),
                RedisKeyConstants.buildPublishedNoteListKey(20L)));
    }
}
