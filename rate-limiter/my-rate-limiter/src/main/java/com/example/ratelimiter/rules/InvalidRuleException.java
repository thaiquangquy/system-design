// src/main/java/com/example/ratelimiter/rules/InvalidRuleException.java
package com.example.ratelimiter.rules;

public class InvalidRuleException extends RuntimeException {
    public InvalidRuleException(String message) {
        super(message);
    }
}
