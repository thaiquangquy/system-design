// src/main/java/com/example/ratelimiter/rules/RuleCache.java
package com.example.ratelimiter.rules;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RuleCache {

  private static final String CACHE_KEY = "ALL";

  private final RuleRepository ruleRepository;
  private final Cache<String, List<Rule>> cache =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(5)).build();

  public Optional<Rule> findMatch(String key) {
    List<Rule> rules = cache.get(CACHE_KEY, ignored -> loadSortedRules());
    return rules.stream().filter(rule -> key.startsWith(rule.keyPrefix())).findFirst();
  }

  public void invalidate() {
    cache.invalidate(CACHE_KEY);
  }

  private List<Rule> loadSortedRules() {
    return ruleRepository.findAll().values().stream()
        .sorted(Comparator.comparingInt((Rule rule) -> rule.keyPrefix().length()).reversed())
        .toList();
  }
}
