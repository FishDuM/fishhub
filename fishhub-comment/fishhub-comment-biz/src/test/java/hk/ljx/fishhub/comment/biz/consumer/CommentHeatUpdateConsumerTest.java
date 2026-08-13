package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentHeatUpdateConsumerTest {

    @Mock
    private CommentDOMapper commentDOMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private CommentHeatUpdateConsumer consumer;

    @Test
    void shouldIgnoreHeatMessageWhenCommentHasBeenDeleted() {
        when(commentDOMapper.selectByCommentIds(List.of(2001L)))
                .thenReturn(Collections.emptyList());

        consumer.onMessage("[2001]");

        verify(commentDOMapper, never()).batchUpdateHeatByCommentIds(anyList(), anyList());
    }
}
