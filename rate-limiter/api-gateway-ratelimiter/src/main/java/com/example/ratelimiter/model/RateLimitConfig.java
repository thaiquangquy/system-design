package com.example.ratelimiter.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RateLimitConfig {
    private String routeId;
    private String algorithm;
    private int capacity;
    private int refillTokens;
    private int refillPeriodInSeconds;

}
