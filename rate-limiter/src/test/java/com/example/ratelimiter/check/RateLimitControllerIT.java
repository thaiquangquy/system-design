package com.example.ratelimiter.check;

import com.example.ratelimiter.rules.RuleService;
import com.example.ratelimiter.support.RedisTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitControllerIT extends RedisTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RuleService ruleService;

    @Test
    void returns200WithHeadersWhenUnderLimit() {
        ruleService.create("ip:9.9.9.9", 5, 60);

        ResponseEntity<Void> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/rate-limit/check?key=ip:9.9.9.9", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("4");
        assertThat(response.getHeaders().get("X-RateLimit-Reset")).isNotEmpty();
    }

    @Test
    void returns429WhenLimitExceeded() {
        ruleService.create("ip:8.8.8.8", 1, 60);
        String checkUrl = "http://localhost:" + port + "/api/v1/rate-limit/check?key=ip:8.8.8.8";

        restTemplate.getForEntity(checkUrl, Void.class);
        ResponseEntity<Void> second = restTemplate.getForEntity(checkUrl, Void.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void returns429WhenNoRuleMatchesKey() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/rate-limit/check?key=unconfigured:xyz", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
