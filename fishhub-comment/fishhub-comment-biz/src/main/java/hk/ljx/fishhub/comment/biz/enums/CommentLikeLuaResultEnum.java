package hk.ljx.fishhub.comment.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
@AllArgsConstructor
public enum CommentLikeLuaResultEnum {
    // 布隆过滤器不存在
    NOT_EXIST(-1L),
    // 评论已点赞
    COMMENT_LIKED(1L),
    // 评论点赞成功
    COMMENT_LIKE_SUCCESS(0L),
    ;

    private final Long code;

    /**
     * 根据类型 code 获取对应的枚举
     *
     * @param code
     * @return
     */
    public static CommentLikeLuaResultEnum valueOf(Long code) {
        for (CommentLikeLuaResultEnum commentLikeLuaResultEnum : CommentLikeLuaResultEnum.values()) {
            if (Objects.equals(code, commentLikeLuaResultEnum.getCode())) {
                return commentLikeLuaResultEnum;
            }
        }
        return null;
    }
}
