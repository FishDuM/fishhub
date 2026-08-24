package hk.ljx.fishhub.search.biz.model.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class SearchNoteReqVO {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    @Min(value = 1, message = "页码不能小于 1")
    @Max(value = 100, message = "由于性能限制，最多仅支持查询前 100 页")
    private Integer pageNo = 1; // 默认值为第一页

    /**
     * 笔记类型：null：综合 / 0：图文 / 1：视频
     */
    private Integer type;

    /**
     * 排序：null：不限 / 0：最新 / 1：最多点赞 / 2：最多评论 / 3：最多收藏
     */
    private Integer sort;

    /**
     * 发布时间范围：null：不限 / 0：一天内 / 1：一周内 / 2：半年内
     */
    private Integer publishTimeRange;

    public SearchNoteReqVO() {
    }

    public SearchNoteReqVO(String keyword, Integer pageNo, Integer type, Integer sort, Integer publishTimeRange) {
        this.keyword = keyword;
        this.pageNo = pageNo != null ? pageNo : 1;
        this.type = type;
        this.sort = sort;
        this.publishTimeRange = publishTimeRange;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getPublishTimeRange() {
        return publishTimeRange;
    }

    public void setPublishTimeRange(Integer publishTimeRange) {
        this.publishTimeRange = publishTimeRange;
    }

    public static SearchNoteReqVOBuilder builder() {
        return new SearchNoteReqVOBuilder();
    }

    public static class SearchNoteReqVOBuilder {
        private String keyword;
        private Integer pageNo = 1;
        private Integer type;
        private Integer sort;
        private Integer publishTimeRange;

        public SearchNoteReqVOBuilder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public SearchNoteReqVOBuilder pageNo(Integer pageNo) {
            this.pageNo = pageNo;
            return this;
        }

        public SearchNoteReqVOBuilder type(Integer type) {
            this.type = type;
            return this;
        }

        public SearchNoteReqVOBuilder sort(Integer sort) {
            this.sort = sort;
            return this;
        }

        public SearchNoteReqVOBuilder publishTimeRange(Integer publishTimeRange) {
            this.publishTimeRange = publishTimeRange;
            return this;
        }

        public SearchNoteReqVO build() {
            return new SearchNoteReqVO(keyword, pageNo, type, sort, publishTimeRange);
        }
    }
}
