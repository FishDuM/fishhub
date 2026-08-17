-- LUA 脚本：创建带 TTL 的空 ZSET
-- 空列表也占位并设置过期时间，避免热空列表反复回源 DB

local key = KEYS[1]
local expireSeconds = ARGV[1]

redis.call('ZADD', key, 0, '')
redis.call('ZREM', key, '')
redis.call('EXPIRE', key, expireSeconds)
return 0
