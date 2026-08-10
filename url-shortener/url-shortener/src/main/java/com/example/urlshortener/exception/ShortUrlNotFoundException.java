package com.example.urlshortener.exception;

public class ShortUrlNotFoundException extends RuntimeException {
    public ShortUrlNotFoundException(String shortUrlCode) {
        super("No long URL found for short code: " + shortUrlCode);
    }
}
