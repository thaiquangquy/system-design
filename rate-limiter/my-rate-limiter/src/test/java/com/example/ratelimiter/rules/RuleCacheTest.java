// src/test/java/com/example/ratelimiter/rules/RuleCacheTest.java
package com.example.ratelimiter.rules;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleCacheTest {

    @Test
    void matchesLongestPrefixWhenMultipleRulesOverlap() {
        RuleRepository repository = mock(RuleRepository.class);
        Map<String, Rule> rules = new LinkedHashMap<>();
        rules.put("user:", new Rule("user:", 1000, 60));
        rules.put("user:vip:", new Rule("user:vip:", 5000, 60));
        when(repository.findAll()).thenReturn(rules);

        RuleCache cache = new RuleCache(repository);

        assertThat(cache.findMatch("user:vip:42"))
                .contains(new Rule("user:vip:", 5000, 60));
        assertThat(cache.findMatch("user:7"))
                .contains(new Rule("user:", 1000, 60));
    }

    @Test
    void returnsEmptyWhenNoRuleMatches() {
        RuleRepository repository = mock(RuleRepository.class);
        when(repository.findAll()).thenReturn(Map.of("user:", new Rule("user:", 1000, 60)));

        RuleCache cache = new RuleCache(repository);

        assertThat(cache.findMatch("ip:1.2.3.4")).isEmpty();
    }

    @Test
    void invalidateForcesReloadFromRepository() {
        RuleRepository repository = mock(RuleRepository.class);
        when(repository.findAll())
                .thenReturn(Map.of("user:", new Rule("user:", 100, 60)))
                .thenReturn(Map.of("user:", new Rule("user:", 200, 60)));

        RuleCache cache = new RuleCache(repository);
        assertThat(cache.findMatch("user:1")).contains(new Rule("user:", 100, 60));

        cache.invalidate();

        assertThat(cache.findMatch("user:1")).contains(new Rule("user:", 200, 60));
    }
}
