package hk.ljx.fishhub.user.relation.biz.model.vo;

import hk.ljx.framework.common.response.Response;
import lombok.Data;

import java.util.List;

@Data
public class RelationCursorPageResponse<T> extends Response<List<T>> {

    private long pageSize;
    private Long nextCursor;

    public static <T> RelationCursorPageResponse<T> success(List<T> data, long pageSize, Long nextCursor) {
        RelationCursorPageResponse<T> response = new RelationCursorPageResponse<>();
        response.setData(data);
        response.setPageSize(pageSize);
        response.setNextCursor(nextCursor);
        return response;
    }
}
