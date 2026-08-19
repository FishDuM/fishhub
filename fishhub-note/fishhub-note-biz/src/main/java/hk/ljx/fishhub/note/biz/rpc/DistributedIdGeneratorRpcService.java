package hk.ljx.fishhub.note.biz.rpc;

import hk.ljx.fishhub.distributed.id.generator.client.DistributedIdGeneratorClient;
import hk.ljx.fishhub.distributed.id.generator.constant.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DistributedIdGeneratorRpcService {

    private final DistributedIdGeneratorClient distributedIdGeneratorClient;

    /**
     * 生成笔记雪花算法 ID
     */
    public String getSnowflakeId() {
        return distributedIdGeneratorClient.getSnowflakeId(ApiConstants.BIZ_TAG_NOTE_ID);
    }
}
