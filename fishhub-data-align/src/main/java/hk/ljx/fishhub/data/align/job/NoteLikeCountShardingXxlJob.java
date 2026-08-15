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
public class NoteLikeCountShardingXxlJob {

    private final ShardedCountAlignmentRunner alignmentRunner;
    private final SelectMapper selectMapper;
    private final UpdateMapper updateMapper;
    private final DeleteMapper deleteMapper;

    @XxlJob("noteLikeCountShardingJobHandler")
    public void alignNoteLikeCount() {
        alignmentRunner.run("笔记点赞数", "t_data_align_note_like_count_temp_",
                suffix -> selectMapper.selectBatchFromDataAlignNoteLikeCountTempTable(suffix, alignmentRunner.batchSize()),
                selectMapper::selectCountFromNoteLikeTableByUserId,
                updateMapper::updateNoteLikeTotalByUserId,
                RedisKeyConstants::buildCountNoteKey,
                RedisKeyConstants.FIELD_LIKE_TOTAL,
                deleteMapper::batchDeleteDataAlignNoteLikeCountTempTable);
    }
}
