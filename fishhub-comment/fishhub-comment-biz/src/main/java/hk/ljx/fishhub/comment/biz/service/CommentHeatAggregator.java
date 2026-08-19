package hk.ljx.fishhub.comment.biz.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 评论热度重算的短窗口聚合器：把时间窗内的多次重算请求合并成一批，
 * 热点评论的高频回复/点赞不再逐事件重算 DB 与 Redis。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentHeatAggregator {

    private static final long WINDOW_MS = 5000;
    private static final int MAX_PENDING = 500;

    private final CommentHeatService commentHeatService;

    private final Set<Long> pending = new HashSet<>();

    public void submit(Collection<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return;
        }
        Set<Long> batch;
        synchronized (pending) {
            pending.addAll(commentIds);
            batch = pending.size() >= MAX_PENDING ? takeBatch() : null;
        }
        if (batch != null) {
            commentHeatService.recomputeHeat(batch);
        }
    }

    @Scheduled(fixedDelay = WINDOW_MS)
    public void flush() {
        Set<Long> batch;
        synchronized (pending) {
            batch = takeBatch();
        }
        if (batch != null) {
            commentHeatService.recomputeHeat(batch);
        }
    }

    private Set<Long> takeBatch() {
        if (pending.isEmpty()) {
            return null;
        }
        Set<Long> batch = new HashSet<>(pending);
        pending.clear();
        return batch;
    }
}
