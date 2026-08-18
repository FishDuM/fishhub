if ARGV[2] == '1' then
    local added = redis.call('SADD', KEYS[2], ARGV[1])
    if added == 1 then
        redis.call('ZADD', KEYS[3], ARGV[4], ARGV[1])
        redis.call('HINCRBY', KEYS[1], 'likeTotal', 1)
    end
else
    local removed = redis.call('SREM', KEYS[2], ARGV[1])
    if removed == 1 then
        redis.call('ZREM', KEYS[3], ARGV[1])
        local v = redis.call('HINCRBY', KEYS[1], 'likeTotal', -1)
        if v < 0 then
            redis.call('HSET', KEYS[1], 'likeTotal', 0)
        end
    end
end
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
redis.call('EXPIRE', KEYS[3], tonumber(ARGV[3]))
return 0
