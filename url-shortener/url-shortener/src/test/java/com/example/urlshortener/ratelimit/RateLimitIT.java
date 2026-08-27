package com.example.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.shorten.dto.ShortenRequest;
import com.example.urlshortener.support.RestIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class RateLimitIT extends RestIntegrationTestSupport {

  @DynamicPropertySource
  static void rateLimitProperties(DynamicPropertyRegistry registry) {
    registry.add("urlshortener.ratelimit.limit", () -> "2");
    registry.add("urlshortener.ratelimit.window-seconds", () -> "60");
  }

  @Test
  void rejectsRequestsOnceThePerIpLimitIsExceeded() {
    String url = url("/api/v1/shorten");

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
