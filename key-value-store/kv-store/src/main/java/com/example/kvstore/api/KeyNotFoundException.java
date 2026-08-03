package com.example.kvstore.api;

public class KeyNotFoundException extends RuntimeException {

    public KeyNotFoundException(String key) {
        super("Key '%s' not found".formatted(key));
    }
}
