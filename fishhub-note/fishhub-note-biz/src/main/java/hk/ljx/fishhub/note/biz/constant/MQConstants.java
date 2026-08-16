package hk.ljx.fishhub.note.biz.constant;


public interface MQConstants {

    /**
     * Topic: 笔记变更统一事件（发布、编辑、删除），由 count / data-align / 本模块以各自 consumer group 订阅
     */
    String TOPIC_NOTE_CHANGED = "NoteChangedTopic";

    /**
     * Topic: 点赞、取消点赞共用一个
     */
    String TOPIC_LIKE_OR_UNLIKE = "LikeUnlikeTopic";

    /**
     * Topic: 收藏、取消收藏共用一个
     */
    String TOPIC_COLLECT_OR_UN_COLLECT = "CollectUnCollectTopic";

    /**
     * Topic: 计数 - 笔记点赞数
     */
    String TOPIC_COUNT_NOTE_LIKE = "CountNoteLikeTopic";

    /**
     * Topic: 计数 - 笔记收藏数
     */
    String TOPIC_COUNT_NOTE_COLLECT = "CountNoteCollectTopic";

    /**
     * Tag 标签：点赞
     */
    String TAG_LIKE = "Like";

    /**
     * Tag 标签：取消点赞
     */
    String TAG_UNLIKE = "Unlike";

    /**
     * Tag 标签：收藏
     */
    String TAG_COLLECT = "Collect";

    /**
     * Tag 标签：取消收藏
     */
    String TAG_UN_COLLECT = "UnCollect";
}
