// src/main/java/com/example/ratelimiter/rules/dto/RuleRequest.java
package com.example.ratelimiter.rules.dto;

public record RuleRequest(String keyPrefix, long limit, long windowSeconds) {
}
