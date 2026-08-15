package hk.ljx.fishhub.data.align.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import hk.ljx.fishhub.data.align.domain.mapper.DeleteMapper;
import hk.ljx.fishhub.data.align.domain.mapper.SelectMapper;
import hk.ljx.fishhub.data.align.domain.mapper.UpdateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserLikeCountShardingXxlJob {

    private final ShardedCountAlignmentRunner alignmentRunner;
    private final SelectMapper selectMapper;
    private final UpdateMapper updateMapper;
    private final DeleteMapper deleteMapper;

    @XxlJob("userLikeCountShardingJobHandler")
    public void alignUserLikeCount() {
        alignmentRunner.runUserCount("用户获赞数", "t_data_align_user_like_count_temp_",
                suffix -> selectMapper.selectBatchFromDataAlignUserLikeCountTempTable(suffix, alignmentRunner.batchSize()),
                selectMapper::selectUserLikeCountFromNoteLikeTableByUserId,
                updateMapper::updateUserLikeTotalByUserId,
                deleteMapper::batchDeleteDataAlignUserLikeCountTempTable);
    }
}
