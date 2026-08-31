-- LUA 脚本：校验并添加关注关系

local key = KEYS[1] -- 操作的 Redis Key
local followUserId = ARGV[1] -- 关注的用户ID
local timestamp = ARGV[2] -- 时间戳
local expireSeconds = ARGV[3] -- 过期时间（秒），每次成功操作续期

-- 使用 EXISTS 命令检查 ZSET 是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 若存在 "-1" 哨兵元素，先将其移除，避免污染真实成员总数与物理分页
if redis.call('ZSCORE', key, '-1') then
    redis.call('ZREM', key, '-1')
end

-- 校验关注人数是否上限（最多关注 2000 人）
local size = redis.call('ZCARD', key)
if size >= 2000 then
    return -2
end

-- 校验目标用户是否已经关注
if redis.call('ZSCORE', key, followUserId) then
    return -3
end

-- ZADD 添加关注关系
redis.call('ZADD', key, timestamp, followUserId)
-- 续期
redis.call('EXPIRE', key, expireSeconds)
return 0
