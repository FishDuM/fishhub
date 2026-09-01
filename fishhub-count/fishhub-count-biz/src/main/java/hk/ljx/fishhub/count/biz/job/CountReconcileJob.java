package hk.ljx.fishhub.count.biz.job;

import hk.ljx.fishhub.count.biz.domain.dataobject.CommentCountReconcileBO;
import hk.ljx.fishhub.count.biz.domain.dataobject.IdCountBO;
import hk.ljx.fishhub.count.biz.domain.dataobject.NoteCountDO;
import hk.ljx.fishhub.count.biz.domain.dataobject.UserCountDO;
import hk.ljx.fishhub.count.biz.domain.mapper.CountReconcileDOMapper;
import hk.ljx.framework.common.util.SafeRedisUtil;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 计数数据对账任务（每日定时校准数据）
 * 采用 MyBatis 游标 Keyset 分批 + 单表索引聚合 + 批量落库与缓存失效，彻底避免雪花 ID 步长空循环与慢查询临时表。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CountReconcileJob {

    private static final int BATCH_SIZE = 1000;
    private final CountReconcileDOMapper countReconcileDOMapper;
    private final SafeRedisUtil safeRedisUtil;

    @Scheduled(cron = "0 30 3 * * ?")
    public void reconcile() {
        long start = System.currentTimeMillis();
        int noteRows = reconcileNoteCounts();
        int userRows = reconcileUserCounts();
        int commentRows = reconcileCommentCounts();
        log.info("countDataReconcile 完成: noteRows={}, userRows={}, commentRows={}, cost={}ms",
                noteRows, userRows, commentRows, System.currentTimeMillis() - start);
    }

    /**
     * 笔记维度计数对账
     */
    private int reconcileNoteCounts() {
        long lastId = 0L;
        int totalUpdated = 0;

        while (true) {
            List<Long> noteIds = countReconcileDOMapper.selectNextNoteIds(lastId, BATCH_SIZE);
            if (noteIds == null || noteIds.isEmpty()) {
                break;
            }

            Map<Long, Long> likeMap = toMap(countReconcileDOMapper.countNoteLikes(noteIds));
            Map<Long, Long> collectMap = toMap(countReconcileDOMapper.countNoteCollections(noteIds));
            Map<Long, Long> commentMap = toMap(countReconcileDOMapper.countNoteComments(noteIds));

            List<NoteCountDO> list = noteIds.stream().map(id -> NoteCountDO.builder()
                    .noteId(id)
                    .likeTotal(likeMap.getOrDefault(id, 0L))
                    .collectTotal(collectMap.getOrDefault(id, 0L))
                    .commentTotal(commentMap.getOrDefault(id, 0L))
                    .build()
            ).toList();

            countReconcileDOMapper.batchUpsertNoteCounts(list);
            // 批量失效 Redis 笔记计数缓存，避免旧脏数据滞留
            List<String> noteRedisKeys = noteIds.stream().map(CountKeyConstants::buildCountNoteKey).toList();
            safeRedisUtil.delete(noteRedisKeys);

            totalUpdated += list.size();
            lastId = noteIds.get(noteIds.size() - 1);
        }
        return totalUpdated;
    }

    /**
     * 用户维度计数对账
     */
    private int reconcileUserCounts() {
        long lastId = 0L;
        int totalUpdated = 0;

        while (true) {
            List<Long> userIds = countReconcileDOMapper.selectNextUserIds(lastId, BATCH_SIZE);
            if (userIds == null || userIds.isEmpty()) {
                break;
            }

            Map<Long, Long> fansMap = toMap(countReconcileDOMapper.countUserFans(userIds));
            Map<Long, Long> followingMap = toMap(countReconcileDOMapper.countUserFollowings(userIds));
            Map<Long, Long> noteMap = toMap(countReconcileDOMapper.countUserNotes(userIds));
            Map<Long, Long> likeMap = toMap(countReconcileDOMapper.countUserLikes(userIds));
            Map<Long, Long> collectMap = toMap(countReconcileDOMapper.countUserCollections(userIds));

            List<UserCountDO> list = userIds.stream().map(id -> UserCountDO.builder()
                    .userId(id)
                    .fansTotal(fansMap.getOrDefault(id, 0L))
                    .followingTotal(followingMap.getOrDefault(id, 0L))
                    .noteTotal(noteMap.getOrDefault(id, 0L))
                    .likeTotal(likeMap.getOrDefault(id, 0L))
                    .collectTotal(collectMap.getOrDefault(id, 0L))
                    .build()
            ).toList();

            countReconcileDOMapper.batchUpsertUserCounts(list);
            // 批量失效 Redis 用户计数缓存，避免旧脏数据滞留
            List<String> userRedisKeys = userIds.stream().map(CountKeyConstants::buildCountUserKey).toList();
            safeRedisUtil.delete(userRedisKeys);

            totalUpdated += list.size();
            lastId = userIds.get(userIds.size() - 1);
        }
        return totalUpdated;
    }

    /**
     * 评论维度计数对账
     */
    private int reconcileCommentCounts() {
        long lastId = 0L;
        int totalUpdated = 0;

        while (true) {
            List<Long> commentIds = countReconcileDOMapper.selectNextCommentIds(lastId, BATCH_SIZE);
            if (commentIds == null || commentIds.isEmpty()) {
                break;
            }

            Map<Long, Long> likeMap = toMap(countReconcileDOMapper.countCommentLikes(commentIds));
            Map<Long, Long> childCommentMap = toMap(countReconcileDOMapper.countChildComments(commentIds));

            List<CommentCountReconcileBO> list = commentIds.stream().map(id -> CommentCountReconcileBO.builder()
                    .commentId(id)
                    .likeTotal(likeMap.getOrDefault(id, 0L))
                    .childCommentTotal(childCommentMap.getOrDefault(id, 0L))
                    .build()
            ).toList();

            countReconcileDOMapper.batchUpdateCommentCounts(list);
            // 批量失效 Redis 评论计数缓存
            List<String> commentRedisKeys = commentIds.stream().map(CountKeyConstants::buildCountCommentKey).toList();
            safeRedisUtil.delete(commentRedisKeys);

            totalUpdated += list.size();
            lastId = commentIds.get(commentIds.size() - 1);
        }
        return totalUpdated;
    }

    private static Map<Long, Long> toMap(List<IdCountBO> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream()
                .filter(item -> item != null && item.getId() != null)
                .collect(Collectors.toMap(
                        IdCountBO::getId,
                        item -> item.getCount() == null ? 0L : item.getCount(),
                        (a, b) -> a
                ));
    }
}
