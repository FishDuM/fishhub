package hk.ljx.framework.common.response;

import lombok.Data;

import java.util.List;


@Data
public class PageResponse<T> extends Response<List<T>> {

    private long pageNo; // 当前页码
    private long totalCount; // 总数据量
    private long pageSize; // 每页展示的数据量
    private long totalPage; // 总页数
    public static <T> PageResponse<T> success(List<T> data, long pageNo, long totalCount) {
        return success(data, pageNo, totalCount, 10L);
    }

    public static <T> PageResponse<T> success(List<T> data, long pageNo, long totalCount, long pageSize) {
        PageResponse<T> pageResponse = new PageResponse<>();
        pageResponse.setSuccess(true);
        pageResponse.setData(data);
        pageResponse.setPageNo(pageNo);
        pageResponse.setTotalCount(totalCount);
        pageResponse.setPageSize(pageSize);
        pageResponse.setTotalPage(getTotalPage(totalCount, pageSize));
        return pageResponse;
    }

    /**
     * 获取总页数
     * @return
     */
    public static long getTotalPage(long totalCount, long pageSize) {
        return pageSize == 0 ? 0 : (totalCount + pageSize - 1) / pageSize;
    }

    /**
     * 计算分页查询的 offset
     * @param pageNo
     * @param pageSize
     * @return
     */
    public static long getOffset(long pageNo, long pageSize) {
        return (Math.max(1, pageNo) - 1) * pageSize;
    }

}
