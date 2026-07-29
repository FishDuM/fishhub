package hk.ljx.fishhub.note.biz.service;


import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.biz.model.vo.FindChannelRspVO;

import java.util.List;

public interface ChannelService {

    /**
     * 查询所有频道
     * @return
     */
    Response<List<FindChannelRspVO>> findChannelList();
}

