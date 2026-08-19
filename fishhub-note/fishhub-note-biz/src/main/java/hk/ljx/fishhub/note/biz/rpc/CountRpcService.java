package hk.ljx.fishhub.note.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdsReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;


@Component
@RequiredArgsConstructor
public class CountRpcService {

    private final CountFeignApi countFeignApi;

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

        if (Objects.isNull(response) || !response.isSuccess() || CollUtil.isEmpty(response.getData())) {
            return Collections.emptyList();
        }

        return response.getData();
    }

}
