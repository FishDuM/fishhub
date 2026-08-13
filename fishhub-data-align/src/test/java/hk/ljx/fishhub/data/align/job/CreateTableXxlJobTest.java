package hk.ljx.fishhub.data.align.job;

import hk.ljx.fishhub.data.align.domain.mapper.CreateTableMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateTableXxlJobTest {

    @Mock
    private CreateTableMapper createTableMapper;

    @InjectMocks
    private CreateTableXxlJob createTableXxlJob;

    @Test
    void shouldCreateTodayAndTomorrowTablesWhenServiceStarts() {
        ReflectionTestUtils.setField(createTableXxlJob, "tableShards", 2);

        createTableXxlJob.initializeDailyTables();

        verify(createTableMapper, times(4)).createDataAlignFollowingCountTempTable(anyString());
        verify(createTableMapper, times(4)).createDataAlignFansCountTempTable(anyString());
        verify(createTableMapper, times(4)).createDataAlignNoteCollectCountTempTable(anyString());
        verify(createTableMapper, times(4)).createDataAlignUserCollectCountTempTable(anyString());
        verify(createTableMapper, times(4)).createDataAlignUserLikeCountTempTable(anyString());
        verify(createTableMapper, times(4)).createDataAlignNoteLikeCountTempTable(anyString());
        verify(createTableMapper, times(4)).createDataAlignNotePublishCountTempTable(anyString());
    }
}
