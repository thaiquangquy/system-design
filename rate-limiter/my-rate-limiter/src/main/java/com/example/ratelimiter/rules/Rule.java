package com.example.ratelimiter.rules;

public record Rule(String keyPrefix, long limit, long windowSeconds) {
}
