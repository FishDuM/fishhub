-- LUA 脚本：粉丝列表全量重建（读侧冷启动）

local key = KEYS[1]

-- 遍历 ARGV，将（分数, 值）按顺序填充
local zaddArgs = {}
for i = 1, #ARGV - 1, 2 do
    table.insert(zaddArgs, ARGV[i])
    table.insert(zaddArgs, ARGV[i+1])
end

if #zaddArgs > 0 then
    redis.call('ZADD', key, unpack(zaddArgs))
end
redis.call('EXPIRE', key, ARGV[#ARGV])

-- 只展示最新 5000 条粉丝
local size = redis.call('ZCARD', key)
if size > 5000 then
    redis.call('ZREMRANGEBYRANK', key, 0, size - 5001)
end
return size
