package com.example.ratelimiter.check;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateLimitController {

    private final RateLimitService rateLimitService;

    public RateLimitController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/api/v1/rate-limit/check")
    public ResponseEntity<Void> check(@RequestParam String key) {
        RateLimitResult result = rateLimitService.check(key);
        HttpStatus status = result.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;

        return ResponseEntity.status(status)
                .header("X-RateLimit-Limit", String.valueOf(result.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(result.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(result.resetSeconds()))
                .build();
    }
}
