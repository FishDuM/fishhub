package hk.ljx.fishhub.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteDetailRspVO {

    private Long id;

    /**
     * 笔记聚合版本；编辑时作为 expectedRevision 提交。
     */
    private Long revision;

    private Integer type;

    private String title;

    private String content;

    private String contentUuid;

    private Boolean isContentEmpty;

    private List<String> imgUris;

    private Long topicId;

    private String topicName;

    private Long creatorId;

    private String creatorName;

    private String avatar;

    private String videoUri;

    /**
     * 编辑时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否可见
     */
    private Integer visible;

    /**
     * 当前登录用户是否点赞了
     */
    private Integer isLiked;

    /**
     * 当前登录用户是否收藏了
     */
    private Integer isCollected;

    private Long likeTotal;

    private Long collectTotal;

    private Long commentTotal;

}
