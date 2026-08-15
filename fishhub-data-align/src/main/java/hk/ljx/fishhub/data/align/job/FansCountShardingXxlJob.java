package hk.ljx.fishhub.data.align.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import hk.ljx.fishhub.data.align.domain.mapper.DeleteMapper;
import hk.ljx.fishhub.data.align.domain.mapper.SelectMapper;
import hk.ljx.fishhub.data.align.domain.mapper.UpdateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FansCountShardingXxlJob {

    private final ShardedCountAlignmentRunner alignmentRunner;
    private final SelectMapper selectMapper;
    private final UpdateMapper updateMapper;
    private final DeleteMapper deleteMapper;

    @XxlJob("fansCountShardingJobHandler")
    public void alignFansCount() {
        alignmentRunner.runUserCount("用户粉丝数", "t_data_align_fans_count_temp_",
                suffix -> selectMapper.selectBatchFromDataAlignFansCountTempTable(suffix, alignmentRunner.batchSize()),
                selectMapper::selectCountFromFansTableByUserId,
                updateMapper::updateUserFansTotalByUserId,
                deleteMapper::batchDeleteDataAlignFansCountTempTable);
    }
}
