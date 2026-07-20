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
        String uuid = addNoteContentReqDTO.getUuid();
        String content = addNoteContentReqDTO.getContent();

        NoteContentDO contentDO = NoteContentDO.builder()
                .id(UUID.fromString(uuid))
                .content(content)
                .build();
        noteContentRepository.save(contentDO);
        return Response.success();
    }

    @Override
    public Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {
        String uuid = findNoteContentReqDTO.getUuid();
        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString(uuid));
        // 判断数据是否存在
        if (!optional.isPresent()) {
            throw new BizException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND);
        }
        NoteContentDO noteContentDO = optional.get();
        FindNoteContentRspDTO findNoteContentRspDTO = FindNoteContentRspDTO.builder()
                .uuid(noteContentDO.getId())
                .content(noteContentDO.getContent())
                .build();

        return Response.success(findNoteContentRspDTO);
    }

    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        String uuid = deleteNoteContentReqDTO.getUuid();

        noteContentRepository.deleteById(UUID.fromString(uuid));

        return Response.success();
    }
}
