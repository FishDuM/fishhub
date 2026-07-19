package hk.ljx.fishhub.kv.biz.service.impl;

import hk.ljx.fishhub.kv.biz.domain.dataobject.NoteContentDO;
import hk.ljx.fishhub.kv.biz.domain.repository.NoteContentRepository;
import hk.ljx.fishhub.kv.biz.service.NoteContentService;
import hk.ljx.fishhub.kv.dto.req.AddNoteContentReqDTO;
import hk.ljx.framework.common.response.Response;
import jakarta.annotation.Resource;

import java.util.UUID;

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
}
