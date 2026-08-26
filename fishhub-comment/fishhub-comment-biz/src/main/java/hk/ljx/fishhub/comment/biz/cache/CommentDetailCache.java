package hk.ljx.fishhub.comment.biz.cache;

import hk.ljx.framework.common.util.CacheTtl;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class CommentDetailCache {

    private final StringRedisTemplate stringRedisTemplate;

    public CommentDetailCache(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public List<String> multiGet(List<String> keys) {
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        return values == null ? Collections.nCopies(keys.size(), null) : values;
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    public void putAll(Map<String, String> data) {
        if (data.isEmpty()) {
            return;
        }
        stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    long expireSeconds = CacheTtl.hours(1, 4);
                    operations.opsForValue().set(entry.getKey(), entry.getValue(), expireSeconds, TimeUnit.SECONDS);
                }
                return null;
            }
        });
    }

    public void delete(Collection<String> keys) {
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
