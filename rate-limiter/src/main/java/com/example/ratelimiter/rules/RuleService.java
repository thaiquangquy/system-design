// src/main/java/com/example/ratelimiter/rules/RuleService.java
package com.example.ratelimiter.rules;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class RuleService {

    public static final String INVALIDATION_CHANNEL = "ratelimit:rules:invalidate";

    private final RuleRepository ruleRepository;
    private final RuleCache ruleCache;
    private final StringRedisTemplate redisTemplate;

    public RuleService(RuleRepository ruleRepository, RuleCache ruleCache, StringRedisTemplate redisTemplate) {
        this.ruleRepository = ruleRepository;
        this.ruleCache = ruleCache;
        this.redisTemplate = redisTemplate;
    }

    public Rule create(String keyPrefix, long limit, long windowSeconds) {
        validate(limit, windowSeconds);
        Rule rule = new Rule(keyPrefix, limit, windowSeconds);
        ruleRepository.save(rule);
        publishInvalidation();
        return rule;
    }

    public Rule update(String keyPrefix, long limit, long windowSeconds) {
        if (ruleRepository.findByPrefix(keyPrefix).isEmpty()) {
            throw new RuleNotFoundException(keyPrefix);
        }
        return create(keyPrefix, limit, windowSeconds);
    }

    public Optional<Rule> get(String keyPrefix) {
        return ruleRepository.findByPrefix(keyPrefix);
    }

    public Map<String, Rule> list() {
        return ruleRepository.findAll();
    }

    public void delete(String keyPrefix) {
        boolean removed = ruleRepository.delete(keyPrefix);
        if (!removed) {
            throw new RuleNotFoundException(keyPrefix);
        }
        publishInvalidation();
    }

    private void validate(long limit, long windowSeconds) {
        if (limit <= 0) {
            throw new InvalidRuleException("limit must be positive");
        }
        if (windowSeconds <= 0) {
            throw new InvalidRuleException("windowSeconds must be positive");
        }
    }

    private void publishInvalidation() {
        redisTemplate.convertAndSend(INVALIDATION_CHANNEL, "invalidate");
    }
}
