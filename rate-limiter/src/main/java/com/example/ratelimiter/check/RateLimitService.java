package com.example.ratelimiter.check;

import com.example.ratelimiter.rules.Rule;
import com.example.ratelimiter.rules.RuleCache;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RateLimitService {

    private final RuleCache ruleCache;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> slidingWindowScript;

    public RateLimitService(RuleCache ruleCache, StringRedisTemplate redisTemplate) {
        this.ruleCache = ruleCache;
        this.redisTemplate = redisTemplate;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/sliding_window.lua"));
        script.setResultType(List.class);
        this.slidingWindowScript = script;
    }

    public RateLimitResult check(String key) {
        Optional<Rule> maybeRule = ruleCache.findMatch(key);
        if (maybeRule.isEmpty()) {
            return RateLimitResult.denied(0, 0, 0);
        }
        Rule rule = maybeRule.get();

        long now = System.currentTimeMillis() / 1000;
        long windowId = now / rule.windowSeconds();
        long elapsed = now - windowId * rule.windowSeconds();

        String currentKey = "ratelimit:cnt:" + key + ":" + windowId;
        String previousKey = "ratelimit:cnt:" + key + ":" + (windowId - 1);

        List<Object> result = redisTemplate.execute(
                slidingWindowScript,
                List.of(currentKey, previousKey),
                String.valueOf(rule.limit()),
                String.valueOf(rule.windowSeconds()),
                String.valueOf(elapsed));

        boolean allowed = "1".equals(result.get(0));
        long remaining = Long.parseLong((String) result.get(1));
        long reset = rule.windowSeconds() - elapsed;

        return new RateLimitResult(allowed, rule.limit(), remaining, reset);
    }
}
