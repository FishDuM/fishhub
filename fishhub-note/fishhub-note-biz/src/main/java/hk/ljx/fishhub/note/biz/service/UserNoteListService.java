package hk.ljx.fishhub.note.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.NumberUtils;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindNoteActionListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindNoteActionListRspVO;
import hk.ljx.fishhub.note.biz.model.vo.NoteItemRspVO;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserNoteListService {

    private final NoteDOMapper noteDOMapper;
    private final UserClient userClient;
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

        // 直接读取 NoteDO 内聚的点赞数，免去跨服务 Feign 调用
        List<NoteItemRspVO> notes = noteDOS.stream().map(note -> NoteItemRspVO.builder()
                .noteId(note.getId())
                .type(note.getType())
                .cover(getFirstCover(note.getImgUris()))
                .videoUri(note.getVideoUri())
                .title(note.getTitle())
                .creatorId(note.getCreatorId())
                .likeTotal(note.getLikeCount() != null ? NumberUtils.formatNumberString(note.getLikeCount()) : "0")
                .isLiked(false)
                .build()).collect(Collectors.toList());

        try {
            List<FindUserByIdRspDTO> userList = userClient.findByIds(noteDOS.stream()
                    .map(NoteDO::getCreatorId).distinct().toList());
            if (CollUtil.isNotEmpty(userList)) {
                Map<Long, FindUserByIdRspDTO> users = userList.stream()
                        .collect(Collectors.toMap(FindUserByIdRspDTO::getId, user -> user, (left, right) -> left));
                notes.forEach(note -> {
                    FindUserByIdRspDTO user = users.get(note.getCreatorId());
                    if (user != null) {
                        note.setNickname(user.getNickName());
                        note.setAvatar(user.getAvatar());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("RPC 调用用户服务批量获取用户信息失败，执行降级处理", e);
        }

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

    private static String getFirstCover(String imgUris) {
        if (StringUtils.isBlank(imgUris)) {
            return null;
        }
        int commaIndex = imgUris.indexOf(',');
        return commaIndex >= 0 ? imgUris.substring(0, commaIndex) : imgUris;
    }
}
