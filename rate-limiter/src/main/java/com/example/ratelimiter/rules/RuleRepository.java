package com.example.ratelimiter.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class RuleRepository {

    private static final String RULES_KEY = "ratelimit:rules";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RuleRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(Rule rule) {
        redisTemplate.opsForHash().put(RULES_KEY, rule.keyPrefix(), serialize(rule));
    }

    public Optional<Rule> findByPrefix(String keyPrefix) {
        Object raw = redisTemplate.opsForHash().get(RULES_KEY, keyPrefix);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(keyPrefix, (String) raw));
    }

    public Map<String, Rule> findAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RULES_KEY);
        Map<String, Rule> rules = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String prefix = (String) entry.getKey();
            rules.put(prefix, deserialize(prefix, (String) entry.getValue()));
        }
        return rules;
    }

    public boolean delete(String keyPrefix) {
        Long removed = redisTemplate.opsForHash().delete(RULES_KEY, keyPrefix);
        return removed != null && removed > 0;
    }

    private String serialize(Rule rule) {
        try {
            return objectMapper.writeValueAsString(new RuleValue(rule.limit(), rule.windowSeconds()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize rule for prefix " + rule.keyPrefix(), e);
        }
    }

    private Rule deserialize(String keyPrefix, String json) {
        try {
            RuleValue value = objectMapper.readValue(json, RuleValue.class);
            return new Rule(keyPrefix, value.limit(), value.windowSeconds());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize rule for prefix " + keyPrefix, e);
        }
    }

    private record RuleValue(long limit, long windowSeconds) {
    }
}
