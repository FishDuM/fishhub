package hk.ljx.fishhub.user.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDO {
    private Long id;

    private String fishhubId;

    private String password;

    private String nickname;

    private String avatar;

    private LocalDate birthday;

    private String backgroundImg;

    private String phone;

    private Integer sex;

    private Integer status;

    private String introduction;

    /**
     * 粉丝总数
     */
    private Integer fansCount;

    /**
     * 关注总数
     */
    private Integer followingCount;

    /**
     * 发布笔记数
     */
    private Integer noteCount;

    /**
     * 笔记获得点赞总数
     */
    private Integer likeCount;

    /**
     * 笔记获得收藏总数
     */
    private Integer collectCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;
}