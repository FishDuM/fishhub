package hk.ljx.fishhub.note.biz.model.bo;

import lombok.Data;

/**
 * Redis 中保存的笔记访问控制最小数据集，避免热点读取反复访问 t_note。
 */
@Data
public class NoteAccessSnapshot {
    private Long creatorId;
    private Integer visible;
    private Long revision;
}
