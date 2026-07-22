package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(RateLimiter rateLimiter, SampleApi sampleApi) {

    public record RateLimiter(String baseUrl) {
    }

    public record SampleApi(String baseUrl) {
    }
}
