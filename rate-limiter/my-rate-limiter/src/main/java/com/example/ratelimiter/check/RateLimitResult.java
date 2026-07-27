package com.example.ratelimiter.check;

public record RateLimitResult(boolean allowed, long limit, long remaining, long resetSeconds) {

    public static RateLimitResult denied(long limit, long remaining, long resetSeconds) {
        return new RateLimitResult(false, limit, remaining, resetSeconds);
    }
}
