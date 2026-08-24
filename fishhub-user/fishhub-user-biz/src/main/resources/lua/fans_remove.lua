-- LUA 脚本：粉丝 ZSet 移除（仅当 key 已存在时）

local key = KEYS[1]
local fanUserId = ARGV[1]
local expireSeconds = ARGV[2]

if redis.call('EXISTS', key) == 0 then
    return 0
end

redis.call('ZREM', key, fanUserId)
redis.call('EXPIRE', key, expireSeconds)
return 1
