package hk.ljx.fishhub.data.align.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import hk.ljx.fishhub.data.align.constant.RedisKeyConstants;
import hk.ljx.fishhub.data.align.domain.mapper.DeleteMapper;
import hk.ljx.fishhub.data.align.domain.mapper.SelectMapper;
import hk.ljx.fishhub.data.align.domain.mapper.UpdateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotePublishCountShardingXxlJob {

    private final ShardedCountAlignmentRunner alignmentRunner;
    private final SelectMapper selectMapper;
    private final UpdateMapper updateMapper;
    private final DeleteMapper deleteMapper;

    @XxlJob("notePublishCountShardingJobHandler")
    public void alignNotePublishCount() {
        alignmentRunner.run("用户笔记发布数", "t_data_align_note_publish_count_temp_",
                suffix -> selectMapper.selectBatchFromDataAlignNotePublishCountTempTable(suffix, alignmentRunner.batchSize()),
                selectMapper::selectCountFromNoteTableByUserId,
                updateMapper::updateUserNoteTotalByUserId,
                RedisKeyConstants::buildCountUserKey,
                RedisKeyConstants.FIELD_NOTE_TOTAL,
                deleteMapper::batchDeleteDataAlignNotePublishCountTempTable);
    }
}
