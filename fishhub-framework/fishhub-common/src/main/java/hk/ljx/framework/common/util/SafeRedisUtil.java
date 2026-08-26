package hk.ljx.framework.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 生产级安全 Redis 工具类（自带异常拦截与容灾降级日志）
 *
 * <p>设计目的：
 * 1. 业务层直接调用常规方法（如 get/set/delete），无需手写 try-catch。
 * 2. 当 Redis 出现网络抖动、超时或单点宕机时，自动捕获异常并记录告警日志，
 *    读操作安全返回 null，写操作安全返回 false，使业务流程能够平滑降级（如回源 MySQL），
 *    避免非核心缓存故障拖垮核心业务链路。</p>
 */
@Slf4j
public class SafeRedisUtil {

    private final StringRedisTemplate redisTemplate;

    public SafeRedisUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public StringRedisTemplate getRedisTemplate() {
        return this.redisTemplate;
    }

    // ==========================================
    // 1. Key / String 字符串操作
    // ==========================================

    /**
     * 读取字符串缓存值，发生异常返回 null
     */
    public String get(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，读取缓存失败，key: {}, error: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 读取 JSON 字符串并反序列化为单个对象，发生异常或缓存不存在返回 null
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = get(key);
        if (StringUtils.isBlank(json) || "null".equalsIgnoreCase(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, clazz);
        } catch (Exception e) {
            log.warn("反序列化缓存 JSON 异常，尝试清理损坏缓存，key: {}, error: {}", key, e.getMessage());
            delete(key);
            return null;
        }
    }

    /**
     * 读取 JSON 字符串并反序列化为 List 集合，发生异常或缓存不存在返回 null
     */
    public <T> List<T> getList(String key, Class<T> clazz) {
        String json = get(key);
        if (StringUtils.isBlank(json) || "null".equalsIgnoreCase(json)) {
            return null;
        }
        try {
            return JsonUtils.parseList(json, clazz);
        } catch (Exception e) {
            log.warn("反序列化缓存 JSON List 异常，尝试清理损坏缓存，key: {}, error: {}", key, e.getMessage());
            delete(key);
            return null;
        }
    }

    /**
     * 写入字符串缓存（不过期）
     */
    public boolean set(String key, String value) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.warn("Redis 不可用，写入缓存失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 写入字符串缓存并设置过期时间
     */
    public boolean set(String key, String value, long timeout, TimeUnit unit) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            return true;
        } catch (Exception e) {
            log.warn("Redis 不可用，写入带过期时间的缓存失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 将对象序列化为 JSON 字符串写入缓存并设置过期时间
     */
    public boolean setObject(String key, Object obj, long timeout, TimeUnit unit) {
        if (StringUtils.isBlank(key) || obj == null) {
            return false;
        }
        try {
            String json = JsonUtils.toJsonString(obj);
            return set(key, json, timeout, unit);
        } catch (Exception e) {
            log.warn("序列化并写入对象缓存失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * setNX: 仅在键不存在时设置值（无过期时间）
     */
    public boolean setIfAbsent(String key, String value) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value));
        } catch (Exception e) {
            log.warn("Redis 不可用，setIfAbsent 失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * setNX: 仅在键不存在时设置值并设置过期时间
     */
    public boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
        } catch (Exception e) {
            log.warn("Redis 不可用，setIfAbsent 失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 批量读取多个 Key 对应的值
     */
    public List<String> multiGet(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return redisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn("Redis 不可用，批量读取缓存失败，keysCount: {}, error: {}", keys.size(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 自增
     */
    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.warn("Redis 不可用，自增失败，key: {}, error: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 自减
     */
    public Long decrement(String key, long delta) {
        try {
            return redisTemplate.opsForValue().decrement(key, delta);
        } catch (Exception e) {
            log.warn("Redis 不可用，自减失败，key: {}, error: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 删除单个 Key
     */
    public boolean delete(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.warn("Redis 不可用，删除缓存失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 批量删除多个 Key
     */
    public boolean delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        try {
            Long deleted = redisTemplate.delete(keys);
            return deleted != null && deleted > 0;
        } catch (Exception e) {
            log.warn("Redis 不可用，批量删除缓存失败，keysCount: {}, error: {}", keys.size(), e.getMessage());
            return false;
        }
    }

    /**
     * 判断 Key 是否存在
     */
    public boolean hasKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis 不可用，判断 key 存在失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 设置 Key 的过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.warn("Redis 不可用，设置过期时间失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    // ==========================================
    // 2. Hash 哈希操作
    // ==========================================

    /**
     * 读取 Hash 中的单个字段
     */
    public Object hGet(String key, String field) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(field)) {
            return null;
        }
        try {
            return redisTemplate.opsForHash().get(key, field);
        } catch (Exception e) {
            log.warn("Redis 不可用，读取 Hash 字段失败，key: {}, field: {}, error: {}", key, field, e.getMessage());
            return null;
        }
    }

    /**
     * 批量读取 Hash 中的多个字段
     */
    public List<Object> hMultiGet(String key, Collection<Object> fields) {
        if (StringUtils.isBlank(key) || fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return redisTemplate.opsForHash().multiGet(key, fields);
        } catch (Exception e) {
            log.warn("Redis 不可用，批量读取 Hash 字段失败，key: {}, fieldsCount: {}, error: {}", key, fields.size(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 写入 Hash 字段
     */
    public boolean hPut(String key, String field, Object value) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(field)) {
            return false;
        }
        try {
            redisTemplate.opsForHash().put(key, field, value);
            return true;
        } catch (Exception e) {
            log.warn("Redis 不可用，写入 Hash 字段失败，key: {}, field: {}, error: {}", key, field, e.getMessage());
            return false;
        }
    }

    /**
     * 批量写入 Hash 字典
     */
    public boolean hPutAll(String key, Map<String, String> map) {
        if (StringUtils.isBlank(key) || map == null || map.isEmpty()) {
            return false;
        }
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            log.warn("Redis 不可用，批量写入 Hash 失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 仅当 Hash 字段不存在时写入
     */
    public boolean hPutIfAbsent(String key, String field, Object value) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(field)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForHash().putIfAbsent(key, field, value));
        } catch (Exception e) {
            log.warn("Redis 不可用，hPutIfAbsent 失败，key: {}, field: {}, error: {}", key, field, e.getMessage());
            return false;
        }
    }

    // ==========================================
    // 3. ZSet 有序集合操作
    // ==========================================

    /**
     * 向 ZSet 添加成员与分数
     */
    public boolean zAdd(String key, String value, double score) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
        } catch (Exception e) {
            log.warn("Redis 不可用，zAdd 失败，key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 获取 ZSet 成员的分数
     */
    public Double zScore(String key, String member) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(member)) {
            return null;
        }
        try {
            return redisTemplate.opsForZSet().score(key, member);
        } catch (Exception e) {
            log.warn("Redis 不可用，zScore 读取失败，key: {}, member: {}, error: {}", key, member, e.getMessage());
            return null;
        }
    }

    /**
     * 获取 ZSet 总基数 (Cardinality)
     */
    public Long zCard(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        try {
            return redisTemplate.opsForZSet().zCard(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，zCard 获取失败，key: {}, error: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 获取 ZSet 降序范围元素
     */
    public Set<String> zReverseRange(String key, long start, long end) {
        if (StringUtils.isBlank(key)) {
            return Collections.emptySet();
        }
        try {
            Set<String> members = redisTemplate.opsForZSet().reverseRange(key, start, end);
            return members != null ? members : Collections.emptySet();
        } catch (Exception e) {
            log.warn("Redis 不可用，zReverseRange 获取失败，key: {}, error: {}", key, e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 移除 ZSet 成员
     */
    public Long zRemove(String key, Object... members) {
        if (StringUtils.isBlank(key) || members == null || members.length == 0) {
            return 0L;
        }
        try {
            return redisTemplate.opsForZSet().remove(key, members);
        } catch (Exception e) {
            log.warn("Redis 不可用，zRemove 失败，key: {}, error: {}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * 移除 ZSet 索引范围内的成员
     */
    public Long zRemoveRange(String key, long start, long end) {
        if (StringUtils.isBlank(key)) {
            return 0L;
        }
        try {
            return redisTemplate.opsForZSet().removeRange(key, start, end);
        } catch (Exception e) {
            log.warn("Redis 不可用，zRemoveRange 失败，key: {}, error: {}", key, e.getMessage());
            return 0L;
        }
    }

    // ==========================================
    // 4. Pipeline 管道批处理安全执行
    // ==========================================

    /**
     * 安全执行 Redis 管道操作
     */
    public List<Object> executePipelined(SessionCallback<?> session) {
        if (session == null) {
            return Collections.emptyList();
        }
        try {
            List<Object> results = redisTemplate.executePipelined(session);
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Redis 不可用，executePipelined 执行失败，error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 安全执行 Redis 底层命令管道
     */
    public List<Object> executePipelined(RedisCallback<?> callback) {
        if (callback == null) {
            return Collections.emptyList();
        }
        try {
            List<Object> results = redisTemplate.executePipelined(callback);
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Redis 不可用，executePipelined 执行失败，error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
