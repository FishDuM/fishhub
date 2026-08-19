package hk.ljx.fishhub.note.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.NumberUtils;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindNoteActionListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindNoteActionListRspVO;
import hk.ljx.fishhub.note.biz.model.vo.NoteItemRspVO;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserNoteListService {

    private final NoteDOMapper noteDOMapper;
    private final UserClient userClient;
    private final CountClient countClient;
    private final NoteInteractionCacheService noteInteractionCacheService;

    public Response<FindNoteActionListRspVO> findCollectedNotes(FindNoteActionListReqVO request) {
        return toResponse(noteDOMapper.selectCollectedNoteListByUserIdAndCursor(
                request.getUserId(), request.getCursorTime(), request.getCursorId()));
    }

    public Response<FindNoteActionListRspVO> findLikedNotes(FindNoteActionListReqVO request) {
        return toResponse(noteDOMapper.selectLikedNoteListByUserIdAndCursor(
                request.getUserId(), request.getCursorTime(), request.getCursorId()));
    }

    private Response<FindNoteActionListRspVO> toResponse(List<NoteDO> noteDOS) {
        if (CollUtil.isEmpty(noteDOS)) {
            return Response.success(FindNoteActionListRspVO.builder().notes(Collections.emptyList()).build());
        }

        List<NoteItemRspVO> notes = noteDOS.stream().map(note -> NoteItemRspVO.builder()
                .noteId(note.getId())
                .type(note.getType())
                .cover(StringUtils.isBlank(note.getImgUris()) ? null : StringUtils.split(note.getImgUris(), ',')[0])
                .videoUri(note.getVideoUri())
                .title(note.getTitle())
                .creatorId(note.getCreatorId())
                .likeTotal("0")
                .isLiked(false)
                .build()).collect(Collectors.toList());

        Map<Long, FindUserByIdRspDTO> users = userClient.findByIds(noteDOS.stream()
                .map(NoteDO::getCreatorId).distinct().toList()).stream()
                .collect(Collectors.toMap(FindUserByIdRspDTO::getId, user -> user, (left, right) -> left));
        notes.forEach(note -> {
            FindUserByIdRspDTO user = users.get(note.getCreatorId());
            if (user != null) {
                note.setNickname(user.getNickName());
                note.setAvatar(user.getAvatar());
            }
        });

        applyLikeTotals(notes, countClient.findByNoteIds(noteDOS.stream().map(NoteDO::getId).toList()));
        applyLikeState(notes);
        NoteDO lastNote = noteDOS.get(noteDOS.size() - 1);
        return Response.success(FindNoteActionListRspVO.builder()
                .notes(notes)
                .nextCursorTime(lastNote.getActionTime())
                .nextCursorId(lastNote.getActionId())
                .build());
    }

    private void applyLikeState(List<NoteItemRspVO> notes) {
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null) {
            return;
        }
        Set<Long> likedNoteIds = noteInteractionCacheService.findLikedNoteIds(userId,
                notes.stream().map(NoteItemRspVO::getNoteId).toList());
        notes.forEach(note -> note.setIsLiked(likedNoteIds.contains(note.getNoteId())));
    }

    private void applyLikeTotals(List<NoteItemRspVO> notes, List<FindNoteCountsByIdRspDTO> counts) {
        if (CollUtil.isEmpty(counts)) {
            return;
        }
        Map<Long, FindNoteCountsByIdRspDTO> countByNoteId = counts.stream()
                .collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId, count -> count));
        notes.forEach(note -> {
            FindNoteCountsByIdRspDTO count = countByNoteId.get(note.getNoteId());
            note.setLikeTotal(count != null && count.getLikeTotal() != null
                    ? NumberUtils.formatNumberString(count.getLikeTotal()) : "0");
        });
    }
}
