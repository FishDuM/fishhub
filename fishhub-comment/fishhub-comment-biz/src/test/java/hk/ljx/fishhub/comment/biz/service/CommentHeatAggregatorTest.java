package hk.ljx.fishhub.comment.biz.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CommentHeatAggregatorTest {

    @Mock
    private CommentHeatService commentHeatService;
    @InjectMocks
    private CommentHeatAggregator commentHeatAggregator;

    @Test
    void shouldMergeSubmitsAndRecomputeOnceOnFlush() {
        commentHeatAggregator.submit(Set.of(1L, 2L));
        commentHeatAggregator.submit(Set.of(2L, 3L));

        commentHeatAggregator.flush();

        verify(commentHeatService).recomputeHeat(Set.of(1L, 2L, 3L));
        commentHeatAggregator.flush();
        verify(commentHeatService, times(1)).recomputeHeat(anySet());
    }

    @Test
    void shouldDrainImmediatelyWhenPendingReachesCap() {
        Set<Long> ids = new HashSet<>();
        for (long i = 1; i <= 500; i++) {
            ids.add(i);
        }

        commentHeatAggregator.submit(ids);

        verify(commentHeatService).recomputeHeat(ids);
    }

    @Test
    void shouldIgnoreEmptySubmit() {
        commentHeatAggregator.submit(Collections.emptySet());
        commentHeatAggregator.flush();
        verifyNoInteractions(commentHeatService);
    }
}
