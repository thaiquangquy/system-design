// src/main/java/com/example/ratelimiter/rules/dto/RuleUpdateRequest.java
package com.example.ratelimiter.rules.dto;

public record RuleUpdateRequest(long limit, long windowSeconds) {
}
