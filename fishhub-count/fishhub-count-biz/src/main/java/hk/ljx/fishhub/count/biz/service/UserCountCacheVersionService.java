package hk.ljx.fishhub.count.biz.service;

import hk.ljx.fishhub.count.constant.CountKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * 用户计数缓存版本服务
 */
@Service
@RequiredArgsConstructor
public class UserCountCacheVersionService {

    public static final long VERSION_EXPIRE_SECONDS = 3 * 60 * 60L;

    private final StringRedisTemplate stringRedisTemplate;

    public long currentVersion(Long userId) {
        String value = stringRedisTemplate.opsForValue().get(CountKeyConstants.buildCountUserCacheVersionKey(userId));
        return value == null ? 0L : Long.parseLong(value);
    }

    public List<Long> currentVersions(List<Long> userIds) {
        List<String> keys = userIds.stream().map(CountKeyConstants::buildCountUserCacheVersionKey).toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return userIds.stream().map(id -> 0L).toList();
        }
        return values.stream()
                .map(value -> value == null ? 0L : Long.parseLong(value))
                .toList();
    }

    public void advanceVersion(Long userId) {
        String versionKey = CountKeyConstants.buildCountUserCacheVersionKey(userId);
        stringRedisTemplate.opsForValue().increment(versionKey);
        stringRedisTemplate.expire(versionKey, VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }
}
