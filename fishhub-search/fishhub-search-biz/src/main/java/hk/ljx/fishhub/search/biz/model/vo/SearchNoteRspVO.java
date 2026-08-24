package hk.ljx.fishhub.search.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class SearchNoteRspVO {

    /**
     * 笔记ID
     */
    private Long noteId;

    /** 发布者用户 ID */
    private Long creatorId;

    /**
     * 封面
     */
    private String cover;

    /**
     * 笔记类型（0：图文，1：视频）
     */
    private Integer type;

    /**
     * 视频地址
     */
    private String videoUri;

    /**
     * 标题
     */
    private String title;

    /**
     * 标题：关键词高亮
     */
    private String highlightTitle;

    /**
     * 发布者头像
     */
    private String avatar;

    /**
     * 发布者昵称
     */
    private String nickname;

    /**
     * 最后一次编辑时间
     */
    private String updateTime;

    /**
     * 被评论数
     */
    private String commentTotal;

    /**
     * 被收藏数
     */
    private String collectTotal;

    /**
     * 被点赞总数
     */
    private String likeTotal;

    public SearchNoteRspVO() {
    }

    public SearchNoteRspVO(Long noteId, Long creatorId, String cover, Integer type, String videoUri, String title, String highlightTitle, String avatar, String nickname, String updateTime, String commentTotal, String collectTotal, String likeTotal) {
        this.noteId = noteId;
        this.creatorId = creatorId;
        this.cover = cover;
        this.type = type;
        this.videoUri = videoUri;
        this.title = title;
        this.highlightTitle = highlightTitle;
        this.avatar = avatar;
        this.nickname = nickname;
        this.updateTime = updateTime;
        this.commentTotal = commentTotal;
        this.collectTotal = collectTotal;
        this.likeTotal = likeTotal;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getVideoUri() {
        return videoUri;
    }

    public void setVideoUri(String videoUri) {
        this.videoUri = videoUri;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHighlightTitle() {
        return highlightTitle;
    }

    public void setHighlightTitle(String highlightTitle) {
        this.highlightTitle = highlightTitle;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getCommentTotal() {
        return commentTotal;
    }

    public void setCommentTotal(String commentTotal) {
        this.commentTotal = commentTotal;
    }

    public String getCollectTotal() {
        return collectTotal;
    }

    public void setCollectTotal(String collectTotal) {
        this.collectTotal = collectTotal;
    }

    public String getLikeTotal() {
        return likeTotal;
    }

    public void setLikeTotal(String likeTotal) {
        this.likeTotal = likeTotal;
    }

    public static SearchNoteRspVOBuilder builder() {
        return new SearchNoteRspVOBuilder();
    }

    public static class SearchNoteRspVOBuilder {
        private Long noteId;
        private Long creatorId;
        private String cover;
        private Integer type;
        private String videoUri;
        private String title;
        private String highlightTitle;
        private String avatar;
        private String nickname;
        private String updateTime;
        private String commentTotal;
        private String collectTotal;
        private String likeTotal;

        public SearchNoteRspVOBuilder noteId(Long noteId) {
            this.noteId = noteId;
            return this;
        }

        public SearchNoteRspVOBuilder creatorId(Long creatorId) {
            this.creatorId = creatorId;
            return this;
        }

        public SearchNoteRspVOBuilder cover(String cover) {
            this.cover = cover;
            return this;
        }

        public SearchNoteRspVOBuilder type(Integer type) {
            this.type = type;
            return this;
        }

        public SearchNoteRspVOBuilder videoUri(String videoUri) {
            this.videoUri = videoUri;
            return this;
        }

        public SearchNoteRspVOBuilder title(String title) {
            this.title = title;
            return this;
        }

        public SearchNoteRspVOBuilder highlightTitle(String highlightTitle) {
            this.highlightTitle = highlightTitle;
            return this;
        }

        public SearchNoteRspVOBuilder avatar(String avatar) {
            this.avatar = avatar;
            return this;
        }

        public SearchNoteRspVOBuilder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public SearchNoteRspVOBuilder updateTime(String updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public SearchNoteRspVOBuilder commentTotal(String commentTotal) {
            this.commentTotal = commentTotal;
            return this;
        }

        public SearchNoteRspVOBuilder collectTotal(String collectTotal) {
            this.collectTotal = collectTotal;
            return this;
        }

        public SearchNoteRspVOBuilder likeTotal(String likeTotal) {
            this.likeTotal = likeTotal;
            return this;
        }

        public SearchNoteRspVO build() {
            return new SearchNoteRspVO(noteId, creatorId, cover, type, videoUri, title, highlightTitle, avatar, nickname, updateTime, commentTotal, collectTotal, likeTotal);
        }
    }
}
