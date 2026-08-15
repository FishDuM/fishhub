package hk.ljx.fishhub.data.align.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import hk.ljx.fishhub.data.align.domain.mapper.DeleteMapper;
import hk.ljx.fishhub.data.align.domain.mapper.SelectMapper;
import hk.ljx.fishhub.data.align.domain.mapper.UpdateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCollectCountShardingXxlJob {

    private final ShardedCountAlignmentRunner alignmentRunner;
    private final SelectMapper selectMapper;
    private final UpdateMapper updateMapper;
    private final DeleteMapper deleteMapper;

    @XxlJob("userCollectCountShardingJobHandler")
    public void alignUserCollectCount() {
        alignmentRunner.runUserCount("用户获收藏数", "t_data_align_user_collect_count_temp_",
                suffix -> selectMapper.selectBatchFromDataAlignUserCollectCountTempTable(suffix, alignmentRunner.batchSize()),
                selectMapper::selectUserCollectCountFromNoteCollectionTableByUserId,
                updateMapper::updateUserCollectTotalByUserId,
                deleteMapper::batchDeleteDataAlignUserCollectCountTempTable);
    }
}
