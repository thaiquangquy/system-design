-- src/main/resources/scripts/sliding_window.lua
-- KEYS[1] = current window counter key
-- KEYS[2] = previous window counter key
-- ARGV[1] = limit
-- ARGV[2] = windowSeconds
-- ARGV[3] = elapsed seconds since the current window started

local limit = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local elapsed = tonumber(ARGV[3])

local currentCount = tonumber(redis.call('GET', KEYS[1]) or '0')
local previousCount = tonumber(redis.call('GET', KEYS[2]) or '0')

local weightedCount = previousCount * ((windowSeconds - elapsed) / windowSeconds) + currentCount

if weightedCount + 1 > limit then
    local remaining = math.floor(limit - weightedCount)
    if remaining < 0 then remaining = 0 end
    return {'0', tostring(remaining)}
end

local newCount = redis.call('INCR', KEYS[1])
if newCount == 1 then
    redis.call('EXPIRE', KEYS[1], windowSeconds * 2)
end

local remaining = math.floor(limit - weightedCount - 1)
if remaining < 0 then remaining = 0 end

return {'1', tostring(remaining)}
