-- LUA 脚本：创建带 TTL 的空 ZSET
-- 写入 -1 作为空列表占位哨兵，避免空集合被 Redis 引擎自动回收导致缓存穿透

local key = KEYS[1]
local expireSeconds = ARGV[1]

redis.call('ZADD', key, 0, '-1')
redis.call('EXPIRE', key, expireSeconds)
return 0
