package hk.ljx.fishhub.note.biz.enums;

/**
 * 笔记可选字段的更新语义。禁止用 JSON null 同时表达“未修改”和“清空”。
 */
public enum NoteUpdateOperationEnum {
    KEEP,
    SET,
    CLEAR,
    REPLACE
}
