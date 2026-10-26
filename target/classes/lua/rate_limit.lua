-- 固定窗口分布式限流
-- KEYS[1] 限流 key（含窗口序号，天然实现窗口切换）
-- ARGV[1] 窗口内最大请求数
-- ARGV[2] 窗口毫秒数
-- 返回 {是否放行, 当前计数, 剩余毫秒}
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
local ttl = redis.call('PTTL', KEYS[1])
if current > tonumber(ARGV[1]) then
    return {0, current, ttl}
end
return {1, current, 0}
