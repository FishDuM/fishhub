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
public class NoteCollectCountShardingXxlJob {

    private final ShardedCountAlignmentRunner alignmentRunner;
    private final SelectMapper selectMapper;
    private final UpdateMapper updateMapper;
    private final DeleteMapper deleteMapper;

    @XxlJob("noteCollectCountShardingJobHandler")
    public void alignNoteCollectCount() {
        alignmentRunner.run("笔记收藏数", "t_data_align_note_collect_count_temp_",
                suffix -> selectMapper.selectBatchFromDataAlignNoteCollectCountTempTable(suffix, alignmentRunner.batchSize()),
                selectMapper::selectCountFromNoteCollectionTableByUserId,
                updateMapper::updateNoteCollectTotalByUserId,
                RedisKeyConstants::buildCountNoteKey,
                RedisKeyConstants.FIELD_COLLECT_TOTAL,
                deleteMapper::batchDeleteDataAlignNoteCollectCountTempTable);
    }
}
