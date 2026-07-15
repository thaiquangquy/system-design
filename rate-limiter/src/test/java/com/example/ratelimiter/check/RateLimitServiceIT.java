package com.example.ratelimiter.check;

import com.example.ratelimiter.rules.RuleService;
import com.example.ratelimiter.support.RedisTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    @Test
    void weightedPreviousWindowCountReducesCurrentWindowAllowance() {
        ruleService.create("ip:3.3.3.3", 10, 100);

        long windowSeconds = 100;
        long limit = 10;
        long previousCount = 5;

        // NOTE: RateLimitService reads the real system clock (it has no injectable clock),
        // so "elapsed" (seconds into the current window) is whatever the wall clock happens
        // to be when this test runs. Elapsed is effectively (now mod windowSeconds), which is
        // uniformly distributed across the *entire* window regardless of how large
        // windowSeconds is -- a large window does NOT make elapsed negligible. An earlier
        // version of this test hardcoded the assumption that elapsed ~= 0 and asserted a
        // fixed remaining == 4 / allowed == true; that failed non-deterministically depending
        // on when the test ran (observed failure: remaining == 8 with elapsed ~= 81/100). We
        // instead read elapsed right before calling check() and independently derive the
        // expected weighted-count math (mirroring the Lua script's formula) so the assertion
        // is correct no matter when the test executes.
        long now = System.currentTimeMillis() / 1000;
        long windowId = now / windowSeconds;
        long elapsed = now - windowId * windowSeconds;
        String previousWindowKey = "ratelimit:cnt:ip:3.3.3.3:" + (windowId - 1);
        stringRedisTemplate.opsForValue().set(previousWindowKey, String.valueOf(previousCount));

        RateLimitResult result = rateLimitService.check("ip:3.3.3.3");

        double weightedCount = previousCount * ((windowSeconds - elapsed) / (double) windowSeconds);
        boolean expectedAllowed = weightedCount + 1 <= limit;
        long expectedRemaining = Math.max(0, (long) Math.floor(limit - weightedCount - (expectedAllowed ? 1 : 0)));

        assertThat(result.allowed()).isEqualTo(expectedAllowed);
        assertThat(result.remaining()).isEqualTo(expectedRemaining);

        // Deterministic invariant regardless of phase: previousCount > 0 and elapsed is always
        // strictly less than windowSeconds, so weight is always > 0, meaning the previous
        // window must reduce the allowance below what a naive fixed-window-only counter
        // (blind to the previous window) would give for a fresh window: remaining == limit - 1 == 9.
        assertThat(result.remaining()).isLessThan(limit - 1);
    }

    @Test
    void fullyLoadedPreviousWindowDeniesEvenWithEmptyCurrentWindow() {
        // Use a previous-window count far above the limit (10x) so the denial is deterministic
        // regardless of wall-clock phase. The previous window's weight is minimized at the
        // worst-case phase alignment (elapsed == windowSeconds - 1), where weight == 1/windowSeconds.
        // Even at that minimum, weightedCount = previousCount * (1/windowSeconds) = 100 * 0.1 = 10,
        // which alone already meets the limit before this request is even counted -- so the
        // request is denied at every possible elapsed value in [0, windowSeconds - 1], not just
        // when the test happens to run near a window boundary.
        long windowSeconds = 10;
        long limit = 10;
        long previousCount = 100;
        ruleService.create("ip:4.4.4.4", limit, windowSeconds);

        long now = System.currentTimeMillis() / 1000;
        long windowId = now / windowSeconds;
        String previousWindowKey = "ratelimit:cnt:ip:4.4.4.4:" + (windowId - 1);
        stringRedisTemplate.opsForValue().set(previousWindowKey, String.valueOf(previousCount));

        RateLimitResult result = rateLimitService.check("ip:4.4.4.4");

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0);
    }
}
