package hk.ljx.fishhub.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public enum NoteOperateEnum {
    // 笔记发布
    PUBLISH(1),
    // 笔记删除
    DELETE(0),
    // 笔记编辑
    UPDATE(2),
    ;

    private final Integer code;

}
