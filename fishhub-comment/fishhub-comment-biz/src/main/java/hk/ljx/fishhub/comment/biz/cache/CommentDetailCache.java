package hk.ljx.fishhub.comment.biz.cache;

import cn.hutool.core.util.RandomUtil;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CommentDetailCache {

    private final StringRedisTemplate redisTemplate;

    public CommentDetailCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<String> multiGet(List<String> keys) {
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        return values == null ? Collections.nCopies(keys.size(), null) : values;
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void putAll(Map<String, String> data) {
        if (data.isEmpty()) {
            return;
        }
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                int expireSeconds = 60 * 60 + RandomUtil.randomInt(4 * 60 * 60);
                connection.stringCommands().setEx(
                        serializer.serialize(entry.getKey()),
                        expireSeconds,
                        serializer.serialize(entry.getValue()));
            }
            return null;
        });
    }

    public void delete(Collection<String> keys) {
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
