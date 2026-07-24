package hk.ljx.fishhub.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收藏、取消收藏笔记类型枚举
 */
@Getter
@AllArgsConstructor
public enum CollectUnCollectNoteTypeEnum {
    // 收藏
    COLLECT(1),
    // 取消收藏
    UN_COLLECT(0),
    ;

    private final Integer code;

}