package hk.ljx.fishhub.search.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class SearchUserRspVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 昵称：关键词高亮
     */
    private String highlightNickname;

    /**
     * 头像
     */
    private String avatar;

    private String fishhubId;

    /**
     * 笔记发布总数
     */
    private Long noteTotal;

    /**
     * 粉丝总数
     */
    private String fansTotal;

    public SearchUserRspVO() {
    }

    public SearchUserRspVO(Long userId, String nickname, String highlightNickname, String avatar, String fishhubId, Long noteTotal, String fansTotal) {
        this.userId = userId;
        this.nickname = nickname;
        this.highlightNickname = highlightNickname;
        this.avatar = avatar;
        this.fishhubId = fishhubId;
        this.noteTotal = noteTotal;
        this.fansTotal = fansTotal;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getHighlightNickname() {
        return highlightNickname;
    }

    public void setHighlightNickname(String highlightNickname) {
        this.highlightNickname = highlightNickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getFishhubId() {
        return fishhubId;
    }

    public void setFishhubId(String fishhubId) {
        this.fishhubId = fishhubId;
    }

    public Long getNoteTotal() {
        return noteTotal;
    }

    public void setNoteTotal(Long noteTotal) {
        this.noteTotal = noteTotal;
    }

    public String getFansTotal() {
        return fansTotal;
    }

    public void setFansTotal(String fansTotal) {
        this.fansTotal = fansTotal;
    }

    public static SearchUserRspVOBuilder builder() {
        return new SearchUserRspVOBuilder();
    }

    public static class SearchUserRspVOBuilder {
        private Long userId;
        private String nickname;
        private String highlightNickname;
        private String avatar;
        private String fishhubId;
        private Long noteTotal;
        private String fansTotal;

        public SearchUserRspVOBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public SearchUserRspVOBuilder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public SearchUserRspVOBuilder highlightNickname(String highlightNickname) {
            this.highlightNickname = highlightNickname;
            return this;
        }

        public SearchUserRspVOBuilder avatar(String avatar) {
            this.avatar = avatar;
            return this;
        }

        public SearchUserRspVOBuilder fishhubId(String fishhubId) {
            this.fishhubId = fishhubId;
            return this;
        }

        public SearchUserRspVOBuilder noteTotal(Long noteTotal) {
            this.noteTotal = noteTotal;
            return this;
        }

        public SearchUserRspVOBuilder fansTotal(String fansTotal) {
            this.fansTotal = fansTotal;
            return this;
        }

        public SearchUserRspVO build() {
            return new SearchUserRspVO(userId, nickname, highlightNickname, avatar, fishhubId, noteTotal, fansTotal);
        }
    }
}
