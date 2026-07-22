package com.example.sampleapi.api;

public record LoginResponse(String token, long expiresIn) {
}
