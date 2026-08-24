package hk.ljx.fishhub.search.biz.model.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class SearchUserReqVO {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    @Min(value = 1, message = "页码不能小于 1")
    @Max(value = 100, message = "由于性能限制，最多仅支持查询前 100 页")
    private Integer pageNo = 1; // 默认值为第一页

    public SearchUserReqVO() {
    }

    public SearchUserReqVO(String keyword, Integer pageNo) {
        this.keyword = keyword;
        this.pageNo = pageNo != null ? pageNo : 1;
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

    public static SearchUserReqVOBuilder builder() {
        return new SearchUserReqVOBuilder();
    }

    public static class SearchUserReqVOBuilder {
        private String keyword;
        private Integer pageNo = 1;

        public SearchUserReqVOBuilder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public SearchUserReqVOBuilder pageNo(Integer pageNo) {
            this.pageNo = pageNo;
            return this;
        }

        public SearchUserReqVO build() {
            return new SearchUserReqVO(keyword, pageNo);
        }
    }
}
