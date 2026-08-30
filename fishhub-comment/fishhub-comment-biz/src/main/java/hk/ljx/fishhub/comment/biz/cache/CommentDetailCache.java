package hk.ljx.fishhub.comment.biz.cache;

import hk.ljx.fishhub.comment.biz.service.CommentCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommentDetailCache {

    private final CommentCacheService commentCacheService;

    public List<String> multiGet(List<String> keys) {
        return commentCacheService.multiGetCommentDetails(keys);
    }

    public void putAll(Map<String, String> data) {
        commentCacheService.batchPutCommentDetails(data);
    }

    public void delete(Collection<String> keys) {
        commentCacheService.evictCommentDetails(keys);
    }
}

