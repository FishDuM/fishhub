package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentHeatServiceTest {

    @Mock
    private CommentDOMapper commentDOMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private CommentHeatService commentHeatService;

    @Test
    void shouldIgnoreSecondLevelCommentWhenRecomputingHeat() {
        CommentDO child = CommentDO.builder()
                .id(2L)
                .noteId(10L)
                .level(CommentLevelEnum.TWO.getCode())
                .likeTotal(1L)
                .childCommentTotal(0L)
                .build();
        when(commentDOMapper.selectByCommentIds(List.of(2L))).thenReturn(List.of(child));

        commentHeatService.recomputeHeat(Set.of(2L));

        verify(commentDOMapper, never()).batchUpdateHeatByCommentIds(anyList(), anyList());
        verifyNoInteractions(stringRedisTemplate);
    }
}
