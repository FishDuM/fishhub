local value = redis.call('get', KEYS[1])
if value == ARGV[1] then
    redis.call('del', KEYS[1])
    return 1
end
return 0
