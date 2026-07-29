package hk.ljx.fishhub.count.biz.service.impl;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.biz.domain.dataobject.NoteCountDO;
import hk.ljx.fishhub.count.biz.domain.dataobject.UserCountDO;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.service.NoteCountService;
import hk.ljx.fishhub.count.biz.service.UserCountService;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
@Slf4j
public class UserCountServiceImpl implements UserCountService {

    @Resource
    private UserCountDOMapper userCountDOMapper;

    @Override
    public Response<FindUserCountByIdRspDTO> findUserCountData(FindUserCountByIdReqDTO findUserCountByIdReqDTO) {
        Long userId = findUserCountByIdReqDTO.getUserId();

        FindUserCountByIdRspDTO findUserCountByIdRspDTO = FindUserCountByIdRspDTO.builder()
                .userId(userId)
                .build();

        UserCountDO userCountDO = userCountDOMapper.selectByUserId(userId);

        if (Objects.nonNull(userCountDO)) {
            findUserCountByIdRspDTO.setCollectTotal(userCountDO.getCollectTotal());
            findUserCountByIdRspDTO.setFansTotal(userCountDO.getFansTotal());
            findUserCountByIdRspDTO.setNoteTotal(userCountDO.getNoteTotal());
            findUserCountByIdRspDTO.setFollowingTotal(userCountDO.getFollowingTotal());
            findUserCountByIdRspDTO.setLikeTotal(userCountDO.getLikeTotal());
        }

        return Response.success(findUserCountByIdRspDTO);
    }
}

