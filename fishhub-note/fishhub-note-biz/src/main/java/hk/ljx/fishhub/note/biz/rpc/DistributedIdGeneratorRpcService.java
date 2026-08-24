package hk.ljx.fishhub.note.biz.rpc;

import hk.ljx.framework.id.client.DistributedIdGeneratorClient;
import hk.ljx.framework.id.constant.ApiConstants;
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
