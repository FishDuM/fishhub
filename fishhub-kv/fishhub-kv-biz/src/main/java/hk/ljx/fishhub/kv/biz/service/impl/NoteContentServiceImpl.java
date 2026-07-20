package hk.ljx.fishhub.kv.biz.service.impl;

import hk.ljx.fishhub.kv.biz.domain.dataobject.NoteContentDO;
import hk.ljx.fishhub.kv.biz.domain.repository.NoteContentRepository;
import hk.ljx.fishhub.kv.biz.service.NoteContentService;
import hk.ljx.fishhub.kv.dto.req.dto.req.AddNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.dto.req.DeleteNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.dto.req.FindNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.dto.rsp.FindNoteContentRspDTO;
import hk.ljx.fishhub.kv.dto.req.enums.ResponseCodeEnum;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class NoteContentServiceImpl implements NoteContentService {

    @Resource
    private NoteContentRepository noteContentRepository;

    @Override
    public Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {
        Long noteId = addNoteContentReqDTO.getNoteId();
        String content = addNoteContentReqDTO.getContent();

        NoteContentDO contentDO = NoteContentDO.builder()
                .id(UUID.randomUUID())
                .content(content)
                .build();
        noteContentRepository.save(contentDO);
        return Response.success();
    }

    @Override
    public Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {
        String noteId = findNoteContentReqDTO.getNoteId();
        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString(noteId));
        // 判断数据是否存在
        if (!optional.isPresent()) {
            throw new BizException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND);
        }
        NoteContentDO noteContentDO = optional.get();
        FindNoteContentRspDTO findNoteContentRspDTO = FindNoteContentRspDTO.builder()
                .noteId(noteContentDO.getId())
                .content(noteContentDO.getContent())
                .build();

        return Response.success(findNoteContentRspDTO);
    }

    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        String noteId = deleteNoteContentReqDTO.getNoteId();

        noteContentRepository.deleteById(UUID.fromString(noteId));

        return Response.success();
    }
}
