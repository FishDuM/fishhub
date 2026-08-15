package hk.ljx.fishhub.note.biz.model.vo;

import hk.ljx.framework.common.response.Response;
import lombok.Data;

import java.util.List;

/**
 * 发现页专用游标分页响应，避免向全局 PageResponse 扩散 cursor 协议。
 */
@Data
public class DiscoverNotePageResponse<T> extends Response<List<T>> {

    private long pageSize;
    private Long nextCursor;

    public static <T> DiscoverNotePageResponse<T> success(List<T> data, long pageSize, Long nextCursor) {
        DiscoverNotePageResponse<T> response = new DiscoverNotePageResponse<>();
        response.setData(data);
        response.setPageSize(pageSize);
        response.setNextCursor(nextCursor);
        return response;
    }
}
