package com.example.ratelimiter.check;

import com.example.ratelimiter.rules.RuleService;
import com.example.ratelimiter.support.RedisTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RateLimitServiceIT extends RedisTestSupport {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RuleService ruleService;

    @Test
    void allowsRequestsUnderTheLimitAndDeniesOnceExceeded() {
        ruleService.create("ip:1.1.1.1", 3, 60);

        assertThat(rateLimitService.check("ip:1.1.1.1").allowed()).isTrue();
        assertThat(rateLimitService.check("ip:1.1.1.1").allowed()).isTrue();
        assertThat(rateLimitService.check("ip:1.1.1.1").allowed()).isTrue();

        RateLimitResult fourth = rateLimitService.check("ip:1.1.1.1");
        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.remaining()).isEqualTo(0);
    }

    @Test
    void deniesWhenNoRuleMatchesTheKey() {
        RateLimitResult result = rateLimitService.check("unconfigured:key");

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void exactlyLimitRequestsAllowedUnderConcurrency() throws InterruptedException {
        ruleService.create("ip:2.2.2.2", 20, 60);

        int threads = 40;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger allowedCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                if (rateLimitService.check("ip:2.2.2.2").allowed()) {
                    allowedCount.incrementAndGet();
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(allowedCount.get()).isEqualTo(20);
    }
}
