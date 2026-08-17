-- LUA 脚本：粉丝 ZSet 增量写入（仅当 key 已存在时）
-- key 不存在时返回 0，由读侧全量重建，避免出现“只有新粉丝”的半缓存

local key = KEYS[1]
local timestamp = ARGV[1] -- 关注时间戳（score）
local fanUserId = ARGV[2] -- 粉丝用户ID
local expireSeconds = ARGV[3] -- 过期时间（秒）

if redis.call('EXISTS', key) == 0 then
    return 0
end

redis.call('ZADD', key, timestamp, fanUserId)
redis.call('EXPIRE', key, expireSeconds)

-- 只展示最新 5000 条粉丝：超出后裁剪最旧的
local size = redis.call('ZCARD', key)
if size > 5000 then
    redis.call('ZREMRANGEBYRANK', key, 0, size - 5001)
end
return 1
