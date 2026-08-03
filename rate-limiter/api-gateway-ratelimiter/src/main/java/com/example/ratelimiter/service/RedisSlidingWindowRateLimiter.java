package com.example.ratelimiter.service;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.Arrays;

/**
 * Redis-backed sliding window counter rate limiter. Ported from my-rate-limiter's
 * sliding_window.lua, atomically weighing the previous window's count against the
 * current one in a single script so concurrent requests can't race the check-then-increment.
 */
public class RedisSlidingWindowRateLimiter implements RateLimiterService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int limit;
    private final int windowSeconds;

    private static final String LUA_SCRIPT = """
        local currentKey = KEYS[1]
        local previousKey = KEYS[2]

        local limit = tonumber(ARGV[1])
        local windowSeconds = tonumber(ARGV[2])
        local elapsed = tonumber(ARGV[3])

        local currentCount = tonumber(redis.call('GET', currentKey) or '0')
        local previousCount = tonumber(redis.call('GET', previousKey) or '0')

        local weightedCount = previousCount * ((windowSeconds - elapsed) / windowSeconds) + currentCount

        if weightedCount + 1 > limit then
            return 0
        end

        local newCount = redis.call('INCR', currentKey)
        if newCount == 1 then
            redis.call('EXPIRE', currentKey, windowSeconds * 2)
        end

        return 1
    """;

    public RedisSlidingWindowRateLimiter(ReactiveStringRedisTemplate redisTemplate, int limit, int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean isAllowed(String key) {
        long now = System.currentTimeMillis() / 1000;
        long windowId = now / windowSeconds;
        long elapsed = now - windowId * windowSeconds;

        String currentKey = "rate:sw:" + key + ":" + windowId;
        String previousKey = "rate:sw:" + key + ":" + (windowId - 1);

        RedisScript<Long> script = RedisScript.of(LUA_SCRIPT, Long.class);

        Mono<Long> result = redisTemplate.execute(
                script,
                Arrays.asList(currentKey, previousKey),  // KEYS
                Arrays.asList(                           // ARGV
                        String.valueOf(limit),
                        String.valueOf(windowSeconds),
                        String.valueOf(elapsed))
        ).next();

        return result.blockOptional().orElse(0L) == 1;
    }
}
