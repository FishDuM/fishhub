package hk.ljx.fishhub.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.ChannelDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.TopicDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindChannelRspVO;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNoteListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicRspVO;
import hk.ljx.fishhub.note.biz.model.vo.NoteItemRspVO;
import hk.ljx.fishhub.note.biz.rpc.CountRpcService;
import hk.ljx.fishhub.note.biz.rpc.UserRpcService;
import hk.ljx.fishhub.note.biz.service.FeedService;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeedServiceImpl implements FeedService {

    private static final long PAGE_SIZE = 10L;

    @Resource
    private ChannelDOMapper channelDOMapper;
    @Resource
    private TopicDOMapper topicDOMapper;
    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private CountRpcService countRpcService;

    @Override
    public Response<List<FindChannelRspVO>> findChannelList() {
        List<FindChannelRspVO> channels = channelDOMapper.selectAllEnabled().stream()
                .map(channel -> FindChannelRspVO.builder().id(channel.getId()).name(channel.getName()).build())
                .toList();
        return Response.success(channels);
    }

    @Override
    public PageResponse<NoteItemRspVO> findDiscoverNoteList(FindDiscoverNoteListReqVO request) {
        long pageNo = Math.max(1, request.getPageNo());
        Long channelId = request.getChannelId();
        long total = noteDOMapper.selectDiscoverTotalCount(channelId);
        List<NoteDO> noteDOS = total == 0
                ? Collections.emptyList()
                : noteDOMapper.selectDiscoverPageList(channelId, PageResponse.getOffset(pageNo, PAGE_SIZE), PAGE_SIZE);
        return PageResponse.success(toNoteItems(noteDOS), pageNo, total, PAGE_SIZE);
    }

    @Override
    public Response<List<FindTopicRspVO>> findTopicList(FindTopicListReqVO request) {
        List<FindTopicRspVO> topics = topicDOMapper.selectByLikeName(request.getKeyword().trim()).stream()
                .map(topic -> FindTopicRspVO.builder().id(topic.getId()).name(topic.getName()).build())
                .toList();
        return Response.success(topics);
    }

    private List<NoteItemRspVO> toNoteItems(List<NoteDO> noteDOS) {
        if (CollUtil.isEmpty(noteDOS)) {
            return Collections.emptyList();
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

        Map<Long, FindUserByIdRspDTO> users = userRpcService.findByIds(noteDOS.stream()
                        .map(NoteDO::getCreatorId).distinct().toList())
                .stream().collect(Collectors.toMap(FindUserByIdRspDTO::getId, Function.identity(), (left, right) -> left));
        Map<Long, FindNoteCountsByIdRspDTO> counts = safeCounts(noteDOS.stream().map(NoteDO::getId).toList()).stream()
                .collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId, Function.identity(), (left, right) -> left));

        notes.forEach(note -> {
            FindUserByIdRspDTO user = users.get(note.getCreatorId());
            if (user != null) {
                note.setNickname(user.getNickName());
                note.setAvatar(user.getAvatar());
            }
            FindNoteCountsByIdRspDTO count = counts.get(note.getNoteId());
            if (count != null) {
                note.setLikeTotal(String.valueOf(count.getLikeTotal()));
            }
        });
        return notes;
    }

    private List<FindNoteCountsByIdRspDTO> safeCounts(List<Long> noteIds) {
        List<FindNoteCountsByIdRspDTO> counts = countRpcService.findByNoteIds(noteIds);
        return counts == null ? Collections.emptyList() : counts;
    }
}
