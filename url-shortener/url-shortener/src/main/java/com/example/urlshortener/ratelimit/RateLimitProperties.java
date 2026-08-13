package com.example.urlshortener.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds urlshortener.ratelimit.* — per-IP fixed-window throttling config, see RateLimitFilter. */
@ConfigurationProperties(prefix = "urlshortener.ratelimit")
public record RateLimitProperties(int limit, long windowSeconds) {}
