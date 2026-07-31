package hk.ljx.fishhub.note.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdsReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;


@Component
public class CountRpcService {

    @Resource
    private CountFeignApi countFeignApi;

    /**
     * 批量查询笔记计数
     *
     * @param noteIds
     * @return
     */
    public List<FindNoteCountsByIdRspDTO> findByNoteIds(List<Long> noteIds) {
        FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO = new FindNoteCountsByIdsReqDTO();
        findNoteCountsByIdsReqDTO.setNoteIds(noteIds);

        Response<List<FindNoteCountsByIdRspDTO>> response = countFeignApi.findNotesCount(findNoteCountsByIdsReqDTO);

        if (!response.isSuccess() || Objects.isNull(response.getData()) || CollUtil.isEmpty(response.getData())) {
            return null;
        }

        return response.getData();
    }

}
