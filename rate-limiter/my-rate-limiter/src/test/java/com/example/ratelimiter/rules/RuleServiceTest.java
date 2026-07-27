// src/test/java/com/example/ratelimiter/rules/RuleServiceTest.java
package com.example.ratelimiter.rules;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleServiceTest {

    @Test
    void createSavesRuleAndPublishesInvalidation() {
        RuleRepository repository = mock(RuleRepository.class);
        RuleCache cache = mock(RuleCache.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RuleService service = new RuleService(repository, cache, redisTemplate);

        Rule created = service.create("user:", 100, 60);

        assertThat(created).isEqualTo(new Rule("user:", 100, 60));
        verify(repository).save(new Rule("user:", 100, 60));
        verify(redisTemplate).convertAndSend(RuleService.INVALIDATION_CHANNEL, "invalidate");
    }

    @Test
    void createRejectsNonPositiveLimit() {
        RuleService service = new RuleService(mock(RuleRepository.class), mock(RuleCache.class), mock(StringRedisTemplate.class));

        assertThatThrownBy(() -> service.create("user:", 0, 60))
                .isInstanceOf(InvalidRuleException.class);
    }

    @Test
    void updateThrowsWhenRuleDoesNotExist() {
        RuleRepository repository = mock(RuleRepository.class);
        when(repository.findByPrefix("user:")).thenReturn(Optional.empty());
        RuleService service = new RuleService(repository, mock(RuleCache.class), mock(StringRedisTemplate.class));

        assertThatThrownBy(() -> service.update("user:", 100, 60))
                .isInstanceOf(RuleNotFoundException.class);
    }

    @Test
    void deleteThrowsWhenRuleDoesNotExist() {
        RuleRepository repository = mock(RuleRepository.class);
        when(repository.delete("user:")).thenReturn(false);
        RuleService service = new RuleService(repository, mock(RuleCache.class), mock(StringRedisTemplate.class));

        assertThatThrownBy(() -> service.delete("user:"))
                .isInstanceOf(RuleNotFoundException.class);
    }

    @Test
    void listDelegatesToRepository() {
        RuleRepository repository = mock(RuleRepository.class);
        Map<String, Rule> rules = Map.of("user:", new Rule("user:", 100, 60));
        when(repository.findAll()).thenReturn(rules);
        RuleService service = new RuleService(repository, mock(RuleCache.class), mock(StringRedisTemplate.class));

        assertThat(service.list()).isEqualTo(rules);
    }
}
