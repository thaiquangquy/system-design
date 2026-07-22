package com.example.gateway.forward;

import com.example.gateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class RateLimitGatewayClient {

    private static final String HEADER_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RESET = "X-RateLimit-Reset";

    private final RestTemplate restTemplate;
    private final GatewayProperties properties;

    public RateLimitCheckResult check(String key) {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.rateLimiter().baseUrl() + "/api/v1/rate-limit/check")
                .queryParam("key", key)
                .encode()
                .toUriString();

        try {
            ResponseEntity<Void> response = restTemplate.getForEntity(url, Void.class);
            return new RateLimitCheckResult(true, HttpStatus.OK, response.getHeaders()));
        } catch (HttpClientErrorException.TooManyRequests ex) {
            return new RateLimitCheckResult(false, HttpStatus.TOO_MANY_REQUESTS, response.getHeaders()));
        } catch (RestClientException ex) {
            return RateLimitCheckResult.unreachable();
        }
    }
}
