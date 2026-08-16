package hk.ljx.fishhub.count.biz.service;

import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * 为用户计数快照提供 fencing version。旧查询即使晚到，只会写入旧版本 Key，不会重新被读取。
 */
@Service
public class UserCountCacheVersionService {

    public static final long VERSION_EXPIRE_SECONDS = 3 * 60 * 60L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long currentVersion(Long userId) {
        String value = stringRedisTemplate.opsForValue().get(RedisKeyConstants.buildCountUserCacheVersionKey(userId));
        return value == null ? 0L : Long.parseLong(value);
    }

    public List<Long> currentVersions(List<Long> userIds) {
        List<String> keys = userIds.stream().map(RedisKeyConstants::buildCountUserCacheVersionKey).toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        return values.stream()
                .map(value -> value == null ? 0L : Long.parseLong(value))
                .toList();
    }

    public void advanceVersion(Long userId) {
        String versionKey = RedisKeyConstants.buildCountUserCacheVersionKey(userId);
        stringRedisTemplate.opsForValue().increment(versionKey);
        stringRedisTemplate.expire(versionKey, VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }
}
