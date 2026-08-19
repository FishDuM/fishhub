package hk.ljx.fishhub.search.biz.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class CanalScheduleTest {

    @Test
    void shouldRebuildNoteDocumentWhenNoteCountChanges() throws Exception {
        CanalSchedule schedule = spy(new CanalSchedule(null, null, null, null));
        doNothing().when(schedule).syncNoteIndex(11L);

        schedule.processEvent(Map.of("note_id", "11"), "t_note_count", CanalEntry.EventType.UPDATE);

        verify(schedule).syncNoteIndex(11L);
    }

    @Test
    void shouldRebuildUserDocumentWhenUserCountChanges() throws Exception {
        CanalSchedule schedule = spy(new CanalSchedule(null, null, null, null));
        doNothing().when(schedule).syncUserIndex(22L);

        schedule.processEvent(Map.of("user_id", "22"), "t_user_count", CanalEntry.EventType.UPDATE);

        verify(schedule).syncUserIndex(22L);
    }
}
