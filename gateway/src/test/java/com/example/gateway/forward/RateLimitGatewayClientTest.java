package com.example.gateway.forward;

import com.example.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitGatewayClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final GatewayProperties properties =
            new GatewayProperties(new GatewayProperties.RateLimiter("http://localhost:8080"), null);
    private final RateLimitGatewayClient client = new RateLimitGatewayClient(restTemplate, properties);

    @Test
    void allowedResponseExtractsRateLimitHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "99");
        headers.set("X-RateLimit-Reset", "60");
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Void.class)))
                .thenReturn(new ResponseEntity<>(headers, HttpStatus.OK));

        RateLimitCheckResult result = client.check("route:login:ip:127.0.0.1");

        assertThat(result.allowed()).isTrue();
        assertThat(result.status()).isEqualTo(HttpStatus.OK);
        assertThat(result.rateLimitHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("99");
    }

    @Test
    void tooManyRequestsIsDeniedWithHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "0");
        HttpClientErrorException.TooManyRequests exception =
                (HttpClientErrorException.TooManyRequests) HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], null);
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Void.class)))
                .thenThrow(exception);

        RateLimitCheckResult result = client.check("route:login:ip:127.0.0.1");

        assertThat(result.allowed()).isFalse();
        assertThat(result.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.rateLimitHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void unreachableRateLimiterFailsClosed() {
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Void.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        RateLimitCheckResult result = client.check("route:login:ip:127.0.0.1");

        assertThat(result.allowed()).isFalse();
        assertThat(result.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
