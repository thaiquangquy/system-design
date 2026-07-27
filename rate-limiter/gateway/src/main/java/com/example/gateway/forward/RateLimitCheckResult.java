package com.example.gateway.forward;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public record RateLimitCheckResult(boolean allowed, HttpStatus status, HttpHeaders rateLimitHeaders) {

    public static RateLimitCheckResult unreachable() {
        return new RateLimitCheckResult(false, HttpStatus.SERVICE_UNAVAILABLE, new HttpHeaders());
    }
}
