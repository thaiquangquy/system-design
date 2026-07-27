// src/main/java/com/example/ratelimiter/rules/RuleService.java
package com.example.ratelimiter.rules;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.example.ratelimiter.rules.dto.RuleConstants.KEY_PREFIX_REGEX;

@Service
@RequiredArgsConstructor
public class RuleService {

    public static final String INVALIDATION_CHANNEL = "ratelimit:rules:invalidate";

    private static final Pattern KEY_PREFIX_PATTERN = Pattern.compile(KEY_PREFIX_REGEX);

    private final RuleRepository ruleRepository;
    private final RuleCache ruleCache;
    private final StringRedisTemplate redisTemplate;

    public Rule create(String keyPrefix, long limit, long windowSeconds) {
        validateKeyPrefix(keyPrefix);
        validate(limit, windowSeconds);
        Rule rule = new Rule(keyPrefix, limit, windowSeconds);
        ruleRepository.save(rule);
        publishInvalidation();
        return rule;
    }

    public Rule update(String keyPrefix, long limit, long windowSeconds) {
        validateKeyPrefix(keyPrefix);
        if (ruleRepository.findByPrefix(keyPrefix).isEmpty()) {
            throw new RuleNotFoundException(keyPrefix);
        }
        return create(keyPrefix, limit, windowSeconds);
    }

    public Optional<Rule> get(String keyPrefix) {
        validateKeyPrefix(keyPrefix);
        return ruleRepository.findByPrefix(keyPrefix);
    }

    public Map<String, Rule> list() {
        return ruleRepository.findAll();
    }

    public void delete(String keyPrefix) {
        validateKeyPrefix(keyPrefix);
        boolean removed = ruleRepository.delete(keyPrefix);
        if (!removed) {
            throw new RuleNotFoundException(keyPrefix);
        }
        publishInvalidation();
    }

    private void validateKeyPrefix(String keyPrefix) {
        if (keyPrefix == null || !KEY_PREFIX_PATTERN.matcher(keyPrefix).matches()) {
            throw new InvalidRuleException("keyPrefix must match " + KEY_PREFIX_PATTERN.pattern());
        }
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
        ruleCache.invalidate();
        redisTemplate.convertAndSend(INVALIDATION_CHANNEL, "invalidate");
    }
}
