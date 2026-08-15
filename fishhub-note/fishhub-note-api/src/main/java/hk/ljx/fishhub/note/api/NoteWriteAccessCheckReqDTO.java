package hk.ljx.fishhub.note.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步写入消费者使用的笔记可写性校验条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteWriteAccessCheckReqDTO {

    private Long noteId;

    private Long userId;
}
