package hk.ljx.fishhub.count.client;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdsReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdsReqDTO;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 计数服务 RPC 客户端
 */
@RequiredArgsConstructor
public class CountClient {

    private final CountFeignApi countFeignApi;

    /**
     * 批量查询笔记计数
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

    /**
     * 查询用户计数信息
     */
    public FindUserCountsByIdRspDTO findUserCountById(Long userId) {
        FindUserCountsByIdReqDTO findUserCountsByIdReqDTO = new FindUserCountsByIdReqDTO();
        findUserCountsByIdReqDTO.setUserId(userId);

        Response<FindUserCountsByIdRspDTO> response = countFeignApi.findUserCount(findUserCountsByIdReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    /**
     * 批量查询用户计数
     */
    public List<FindUserCountsByIdRspDTO> findByUserIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }

        Response<List<FindUserCountsByIdRspDTO>> response = countFeignApi.findUsersCount(
                FindUserCountsByIdsReqDTO.builder().userIds(userIds).build());
        if (Objects.isNull(response) || !response.isSuccess() || CollUtil.isEmpty(response.getData())) {
            return Collections.emptyList();
        }
        return response.getData();
    }
}
