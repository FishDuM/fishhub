-- 校验并消费验证码（带失败计数，防止 6 位数字验证码在 TTL 内被穷举）
-- KEYS[1] = 验证码 key
-- ARGV[1] = 用户提交的验证码
-- ARGV[2] = 最大允许失败次数（超过即作废验证码）
-- 返回：1=校验成功并消费  0=验证码错误（已累计失败次数）  -1=验证码不存在/已过期/已消费  -2=失败次数超限，验证码已作废
local value = redis.call('GET', KEYS[1])
if value == false then
    return -1
end
if value == ARGV[1] then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[1] .. ':fail')
    return 1
end
local failKey = KEYS[1] .. ':fail'
local fails = redis.call('INCR', failKey)
if fails == 1 then
    -- 失败计数与验证码同生命周期，避免验证码过期后计数残留
    local ttl = redis.call('TTL', KEYS[1])
    if ttl and ttl > 0 then
        redis.call('EXPIRE', failKey, ttl)
    end
end
if fails >= tonumber(ARGV[2]) then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', failKey)
    return -2
end
return 0
