package hk.ljx.fishhub.note.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 笔记变更统一事件：一个业务事务（发布/编辑/删除）只产生一条本事件，
 * 由各消费方以独立 consumer group 订阅、按 changeType 过滤。
 * 字段必须保持确定性（不含时间戳/随机 ID），消息体重投递时可被下游幂等去重。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteChangedEventMqDTO {

    /**
     * 笔记 ID
     */
    private Long noteId;

    /**
     * 笔记发布者 ID
     */
    private Long creatorId;

    /**
     * 变更类型，见 NoteOperateEnum：0 - 删除； 1 - 发布； 2 - 编辑
     */
    private Integer changeType;

    /**
     * 正文写入/删除任务，可为空。任务随笔记事务原子提交，消费者幂等同步到 KV。
     */
    private List<NoteContentTaskMqDTO> contentTasks;

}
