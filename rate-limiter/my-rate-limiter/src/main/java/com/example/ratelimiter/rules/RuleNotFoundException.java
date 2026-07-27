// src/main/java/com/example/ratelimiter/rules/RuleNotFoundException.java
package com.example.ratelimiter.rules;

public class RuleNotFoundException extends RuntimeException {
    public RuleNotFoundException(String keyPrefix) {
        super("No rule found for prefix: " + keyPrefix);
    }
}
