package hk.ljx.fishhub.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteDO {
    private Long id;

    private String title;

    private Boolean isContentEmpty;

    private Long creatorId;

    private Long channelId;

    private Long topicId;

    private String topicName;

    private Boolean isTop;

    private Integer type;

    private String imgUris;

    private String videoUri;

    private Integer visible;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer status;

    private String contentUuid;

    /**
     * 收藏或点赞关系记录 ID，仅用于互动列表的复合游标。
     */
    private Long actionId;

    /**
     * 最近一次收藏或点赞时间，仅用于互动列表排序与分页。
     */
    private LocalDateTime actionTime;

    /**
     * 笔记聚合版本，用于编辑乐观锁与缓存版本校验。
     */
    private Long revision;
}
