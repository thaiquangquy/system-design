package com.example.ratelimiter.rules;

import com.example.ratelimiter.support.RedisTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RuleRepositoryTest extends RedisTestSupport {

    @Autowired
    private RuleRepository ruleRepository;

    @Test
    void savesAndFindsRuleByPrefix() {
        ruleRepository.save(new Rule("user:", 100, 60));

        assertThat(ruleRepository.findByPrefix("user:"))
                .contains(new Rule("user:", 100, 60));
    }

    @Test
    void findAllReturnsEveryStoredRule() {
        ruleRepository.save(new Rule("user:", 100, 60));
        ruleRepository.save(new Rule("ip:", 10, 1));

        Map<String, Rule> rules = ruleRepository.findAll();

        assertThat(rules)
                .containsEntry("user:", new Rule("user:", 100, 60))
                .containsEntry("ip:", new Rule("ip:", 10, 1));
    }

    @Test
    void deleteRemovesRuleAndReturnsTrue() {
        ruleRepository.save(new Rule("user:", 100, 60));

        boolean removed = ruleRepository.delete("user:");

        assertThat(removed).isTrue();
        assertThat(ruleRepository.findByPrefix("user:")).isEmpty();
    }

    @Test
    void deleteReturnsFalseWhenRuleDoesNotExist() {
        assertThat(ruleRepository.delete("missing:")).isFalse();
    }
}
