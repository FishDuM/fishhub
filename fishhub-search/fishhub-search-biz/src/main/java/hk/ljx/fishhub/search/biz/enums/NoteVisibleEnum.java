package hk.ljx.fishhub.search.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;


public enum NoteVisibleEnum {

    PUBLIC(0), // 公开，所有人可见
    PRIVATE(1); // 仅自己可见

    private final Integer code;

    NoteVisibleEnum(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

}
