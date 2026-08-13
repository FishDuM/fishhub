package hk.ljx.fishhub.count.fallback;

import com.google.common.collect.Lists;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdsReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdsReqDTO;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class CountFeignApiFallback implements CountFeignApi {

    /**
     * 查询用户计数降级
     *
     * @param findUserCountsByIdReqDTO
     * @return
     */
    @Override
    public Response<FindUserCountsByIdRspDTO> findUserCount(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO) {
        // 要查询的用户 ID
        Long userId = findUserCountsByIdReqDTO.getUserId();

        // 降级后，所有计数默认为 0
        return Response.success(FindUserCountsByIdRspDTO.builder()
                        .userId(userId)
                        .noteTotal(0L)
                        .likeTotal(0L)
                        .followingTotal(0L)
                        .fansTotal(0L)
                        .collectTotal(0L)
                        .build());
    }

    @Override
    public Response<List<FindUserCountsByIdRspDTO>> findUsersCount(FindUserCountsByIdsReqDTO findUserCountsByIdsReqDTO) {
        List<FindUserCountsByIdRspDTO> counts = findUserCountsByIdsReqDTO.getUserIds().stream()
                .distinct()
                .map(userId -> FindUserCountsByIdRspDTO.builder()
                        .userId(userId)
                        .noteTotal(0L)
                        .likeTotal(0L)
                        .followingTotal(0L)
                        .fansTotal(0L)
                        .collectTotal(0L)
                        .build())
                .toList();
        return Response.success(counts);
    }

    /**
     * 批量查询笔记计数降级
     *
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    @Override
    public Response<List<FindNoteCountsByIdRspDTO>> findNotesCount(FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO) {
        List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = Lists.newArrayList();

        List<Long> noteIds = findNoteCountsByIdsReqDTO.getNoteIds();

        noteIds.forEach(noteId ->
            findNoteCountsByIdRspDTOS.add(FindNoteCountsByIdRspDTO.builder()
                            .noteId(noteId)
                            .collectTotal(0L)
                            .commentTotal(0L)
                            .likeTotal(0L)
                            .build())
        );

        return Response.success(findNoteCountsByIdRspDTOS);
    }

}
