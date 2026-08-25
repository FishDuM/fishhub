-- ==============================================================================
-- FishHub Cassandra 初始化建表脚本
-- 适用数据库：Apache Cassandra 4.x / 5.x
-- 说明：用于存储高并发大文本数据（笔记正文、评论正文），实现元数据与大文本分离存储
-- 执行方式：
--   docker exec -i cassandra cqlsh < sql/cassandra_init.cql
-- ==============================================================================

-- 1. 创建 Keyspace（键空间）
CREATE KEYSPACE IF NOT EXISTS fishhub
WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': '1'
} AND durable_writes = true;

-- 切换到 fishhub 键空间
USE fishhub;


-- ==============================================================================
-- 2. 笔记正文表：note_content
-- 对应实体：hk.ljx.fishhub.note.biz.domain.dataobject.NoteContentDO
-- 存储内容：笔记正文 Markdown/富文本（单条笔记生成独立的 content_id UUID）
-- ==============================================================================
CREATE TABLE IF NOT EXISTS fishhub.note_content (
    id uuid,            -- 正文唯一标识 UUID（对应 MySQL t_note.content_uuid）
    content text,       -- 笔记正文富文本/Markdown 内容
    PRIMARY KEY (id)
) WITH comment = '笔记正文大文本存储表';


-- ==============================================================================
-- 3. 评论正文表：comment_content
-- 对应实体：hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentDO
-- 复合主键设计说明：
--   - 分区键 (note_id, year_month)：同一篇笔记同个月份的评论存储在相同节点/SSTable，
--     查询整页评论时单次网络 I/O 即可极速检索。
--   - 聚簇键 (content_id)：单篇评论正文的 UUID。
-- ==============================================================================
CREATE TABLE IF NOT EXISTS fishhub.comment_content (
    note_id bigint,     -- 所属笔记 ID（分区键）
    year_month text,    -- 发布年月，格式 yyyy-MM，如 2026-08（分区键）
    content_id uuid,    -- 评论正文唯一标识 UUID（聚簇键）
    content text,       -- 评论正文内容
    PRIMARY KEY ((note_id, year_month), content_id)
) WITH CLUSTERING ORDER BY (content_id ASC)
  AND comment = '评论正文存储表（按笔记与年月联合分区）';
