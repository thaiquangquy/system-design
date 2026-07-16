// src/main/java/com/example/ratelimiter/rules/dto/RuleRequest.java
package com.example.ratelimiter.rules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import static com.example.ratelimiter.rules.dto.RuleConstants.KEY_PREFIX_REGEX;

public record RuleRequest(
    @NotBlank
        @Pattern(
            regexp = KEY_PREFIX_REGEX,
            message = "keyPrefix must be alphanumeric with _-:. only")
        String keyPrefix,
    @Positive long limit,
    @Positive long windowSeconds) {
}
