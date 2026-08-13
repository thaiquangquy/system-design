package com.example.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.shorten.dto.ShortenRequest;
import com.example.urlshortener.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT extends IntegrationTestSupport {

    @DynamicPropertySource
    static void rateLimitProperties(DynamicPropertyRegistry registry) {
        registry.add("urlshortener.ratelimit.limit", () -> "2");
        registry.add("urlshortener.ratelimit.window-seconds", () -> "60");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rejectsRequestsOnceThePerIpLimitIsExceeded() {
        String url = "http://localhost:" + port + "/api/v1/shorten";

        var first =
                restTemplate.postForEntity(url, new ShortenRequest("https://example.com/rl-1"), Void.class);
        var second =
                restTemplate.postForEntity(url, new ShortenRequest("https://example.com/rl-2"), Void.class);
        var third =
                restTemplate.postForEntity(url, new ShortenRequest("https://example.com/rl-3"), Void.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(third.getStatusCode().value()).isEqualTo(429);
    }
}
