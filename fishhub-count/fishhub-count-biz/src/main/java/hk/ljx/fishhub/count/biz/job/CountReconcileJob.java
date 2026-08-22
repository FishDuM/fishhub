package hk.ljx.fishhub.count.biz.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 计数数据对账任务（每日定时校准数据）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CountReconcileJob {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 30 3 * * ?")
    public void reconcile() {
        long start = System.currentTimeMillis();
        int noteRows = reconcileNoteCounts();
        int userRows = reconcileUserCounts();
        int commentRows = reconcileCommentCounts();
        log.info("countDataReconcile 完成: noteRows={}, userRows={}, commentRows={}, cost={}ms",
                noteRows, userRows, commentRows, System.currentTimeMillis() - start);
    }

    private int reconcileNoteCounts() {
        String sql = """
                INSERT INTO fishhub_count.t_note_count (note_id, like_total, collect_total, comment_total)
                SELECT n.id,
                       COALESCE(l.cnt, 0),
                       COALESCE(c.cnt, 0),
                       COALESCE(cm.cnt, 0)
                FROM fishhub_note.t_note n
                LEFT JOIN (SELECT note_id, COUNT(*) AS cnt FROM fishhub_note.t_note_like WHERE status = 1 GROUP BY note_id) l ON l.note_id = n.id
                LEFT JOIN (SELECT note_id, COUNT(*) AS cnt FROM fishhub_note.t_note_collection WHERE status = 1 GROUP BY note_id) c ON c.note_id = n.id
                LEFT JOIN (SELECT note_id, COUNT(*) AS cnt FROM fishhub_comment.t_comment WHERE level = 1 GROUP BY note_id) cm ON cm.note_id = n.id
                WHERE n.status = 1
                ON DUPLICATE KEY UPDATE
                    like_total = VALUES(like_total),
                    collect_total = VALUES(collect_total),
                    comment_total = VALUES(comment_total)
                """;
        return jdbcTemplate.update(sql);
    }

    private int reconcileUserCounts() {
        String sql = """
                INSERT INTO fishhub_count.t_user_count (user_id, fans_total, following_total, note_total, like_total, collect_total)
                SELECT u.id,
                       COALESCE(fans.cnt, 0),
                       COALESCE(fl.cnt, 0),
                       COALESCE(nt.cnt, 0),
                       COALESCE(lk.cnt, 0),
                       COALESCE(cl.cnt, 0)
                FROM fishhub_user.t_user u
                LEFT JOIN (SELECT following_user_id AS uid, COUNT(*) AS cnt FROM fishhub_relation.t_following GROUP BY following_user_id) fans ON fans.uid = u.id
                LEFT JOIN (SELECT user_id AS uid, COUNT(*) AS cnt FROM fishhub_relation.t_following GROUP BY user_id) fl ON fl.uid = u.id
                LEFT JOIN (SELECT creator_id AS uid, COUNT(*) AS cnt FROM fishhub_note.t_note WHERE status = 1 GROUP BY creator_id) nt ON nt.uid = u.id
                LEFT JOIN (SELECT n.creator_id AS uid, COUNT(*) AS cnt FROM fishhub_note.t_note_like l JOIN fishhub_note.t_note n ON l.note_id = n.id WHERE l.status = 1 AND n.status = 1 GROUP BY n.creator_id) lk ON lk.uid = u.id
                LEFT JOIN (SELECT n.creator_id AS uid, COUNT(*) AS cnt FROM fishhub_note.t_note_collection c JOIN fishhub_note.t_note n ON c.note_id = n.id WHERE c.status = 1 AND n.status = 1 GROUP BY n.creator_id) cl ON cl.uid = u.id
                WHERE u.is_deleted = 0
                ON DUPLICATE KEY UPDATE
                    fans_total = VALUES(fans_total),
                    following_total = VALUES(following_total),
                    note_total = VALUES(note_total),
                    like_total = VALUES(like_total),
                    collect_total = VALUES(collect_total)
                """;
        return jdbcTemplate.update(sql);
    }

    /**
     * 评论维度计数对账。语义与消费端一致：
     *   like_total          = t_comment_like 中 comment_id 的行数（评论点赞为物理增删行，无 status）
     *   child_comment_total = parent_id = 该评论 id 的直系子评论行数
     */
    private int reconcileCommentCounts() {
        String sql = """
            UPDATE fishhub_comment.t_comment c
            LEFT JOIN (SELECT comment_id AS cid, COUNT(*) AS cnt
                       FROM fishhub_comment.t_comment_like GROUP BY comment_id) lk ON lk.cid = c.id
            LEFT JOIN (SELECT parent_id AS pid, COUNT(*) AS cnt
                       FROM fishhub_comment.t_comment WHERE parent_id IS NOT NULL AND parent_id <> 0 GROUP BY parent_id) ch ON ch.pid = c.id
            SET c.like_total = COALESCE(lk.cnt, 0),
                c.child_comment_total = COALESCE(ch.cnt, 0)
            """;
        return jdbcTemplate.update(sql);
    }
}
