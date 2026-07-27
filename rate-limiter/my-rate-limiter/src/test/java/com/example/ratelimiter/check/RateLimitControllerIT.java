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
        ruleService.create("route:login:", 5, 60);

        ResponseEntity<Void> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/rate-limit/check?key=route:login:&ip=9.9.9.9", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("4");
        assertThat(response.getHeaders().get("X-RateLimit-Reset")).isNotEmpty();
    }

    @Test
    void returns429WhenLimitExceeded() {
        ruleService.create("route:user:", 1, 60);
        String checkUrl = "http://localhost:" + port + "/api/v1/rate-limit/check?key=route:user:&ip=8.8.8.8";

        restTemplate.getForEntity(checkUrl, Void.class);
        ResponseEntity<Void> second = restTemplate.getForEntity(checkUrl, Void.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void eachIpTracksIndependentlyUnderTheSameRule() {
        ruleService.create("route:user:", 1, 60);
        String baseUrl = "http://localhost:" + port + "/api/v1/rate-limit/check?key=route:user:&ip=";

        ResponseEntity<Void> firstIpFirstCall = restTemplate.getForEntity(baseUrl + "5.5.5.5", Void.class);
        ResponseEntity<Void> firstIpSecondCall = restTemplate.getForEntity(baseUrl + "5.5.5.5", Void.class);
        ResponseEntity<Void> secondIpFirstCall = restTemplate.getForEntity(baseUrl + "6.6.6.6", Void.class);

        assertThat(firstIpFirstCall.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstIpSecondCall.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(secondIpFirstCall.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void returns429WhenNoRuleMatchesKey() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/rate-limit/check?key=unconfigured:xyz&ip=1.1.1.1", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
