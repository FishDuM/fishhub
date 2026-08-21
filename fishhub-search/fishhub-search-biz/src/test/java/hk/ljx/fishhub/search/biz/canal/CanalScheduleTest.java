package hk.ljx.fishhub.search.biz.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import hk.ljx.fishhub.search.biz.canal.service.EsIndexSyncAggregator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class CanalScheduleTest {

    @Test
    void shouldSubmitNoteRebuildWhenNoteCountChanges() throws Exception {
        EsIndexSyncAggregator aggregator = mock(EsIndexSyncAggregator.class);
        CanalSchedule schedule = spy(new CanalSchedule(null, null, null, null, aggregator));

        schedule.processEvent(Map.of("note_id", "11"), "t_note_count", CanalEntry.EventType.UPDATE);

        verify(aggregator).submitNote(11L);
        verify(schedule, never()).syncNoteIndex(11L);
    }

    @Test
    void shouldSubmitUserRebuildWhenUserCountChanges() throws Exception {
        EsIndexSyncAggregator aggregator = mock(EsIndexSyncAggregator.class);
        CanalSchedule schedule = spy(new CanalSchedule(null, null, null, null, aggregator));

        schedule.processEvent(Map.of("user_id", "22"), "t_user_count", CanalEntry.EventType.UPDATE);

        verify(aggregator).submitUser(22L);
        verify(schedule, never()).syncUserIndex(22L);
    }

    @Test
    void shouldRebuildNoteIndexWhenOnlyTitleUpdated() throws Exception {
        EsIndexSyncAggregator aggregator = mock(EsIndexSyncAggregator.class);
        CanalSchedule schedule = spy(new CanalSchedule(null, null, null, null, aggregator));
        org.mockito.Mockito.doNothing().when(schedule).syncNoteIndex(11L);

        // UPDATE 事件不携带 status/visible 列（只改 title）——必须触发重建，否则搜索标题陈旧
        schedule.processEvent(Map.of("id", "11", "title", "新标题"), "t_note", CanalEntry.EventType.UPDATE);

        verify(schedule).syncNoteIndex(11L);
    }
}
