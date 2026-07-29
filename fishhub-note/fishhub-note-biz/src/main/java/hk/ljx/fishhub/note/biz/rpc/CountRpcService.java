package hk.ljx.fishhub.note.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdRspDTO;
import hk.ljx.fishhub.user.dto.req.FindUserByIdReqDTO;
import hk.ljx.fishhub.user.dto.req.FindUsersByIdsReqDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CountRpcService {

    @Resource
    private CountFeignApi countFeignApi;

    /**
     * 查询笔记计数信息
     * @param noteId
     * @return
     */
    public FindNoteCountByIdRspDTO findNoteCountById(Long noteId) {
        FindNoteCountByIdReqDTO findNoteCountByIdReqDTO = new FindNoteCountByIdReqDTO();
        findNoteCountByIdReqDTO.setNoteId(noteId);

        Response<FindNoteCountByIdRspDTO> response = countFeignApi.findNoteCount(findNoteCountByIdReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

}

