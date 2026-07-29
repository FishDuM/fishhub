package hk.ljx.fishhub.count.biz.service.impl;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.biz.domain.dataobject.NoteCountDO;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.service.NoteCountService;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
@Slf4j
public class NoteCountServiceImpl implements NoteCountService {

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    /**
     * 查询笔记计数数据
     *
     * @param findNoteCountByIdReqDTO
     * @return
     */
    @Override
    public Response<FindNoteCountByIdRspDTO> findNoteCountData(FindNoteCountByIdReqDTO findNoteCountByIdReqDTO) {
        Long noteId = findNoteCountByIdReqDTO.getNoteId();

        // TODO: 后续需要添加缓存

        NoteCountDO noteCountDO = noteCountDOMapper.selectByNoteId(noteId);

        FindNoteCountByIdRspDTO findNoteCountByIdRspDTO = FindNoteCountByIdRspDTO.builder()
                .noteId(noteId)
                .collectTotal(0L)
                .commentTotal(0L)
                .likeTotal(0L)
                .build();

        if (Objects.nonNull(noteCountDO)) {
            findNoteCountByIdRspDTO.setCollectTotal(noteCountDO.getCollectTotal());
            findNoteCountByIdRspDTO.setCommentTotal(noteCountDO.getCommentTotal());
            findNoteCountByIdRspDTO.setLikeTotal(noteCountDO.getLikeTotal());
        }

        return Response.success(findNoteCountByIdRspDTO);
    }
}

