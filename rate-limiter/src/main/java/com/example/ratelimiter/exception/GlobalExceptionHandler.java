// src/main/java/com/example/ratelimiter/exception/GlobalExceptionHandler.java
package com.example.ratelimiter.exception;

import com.example.ratelimiter.rules.InvalidRuleException;
import com.example.ratelimiter.rules.RuleNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidRuleException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(InvalidRuleException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RuleNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(RuleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
