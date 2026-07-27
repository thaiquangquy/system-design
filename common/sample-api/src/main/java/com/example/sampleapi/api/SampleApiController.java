package com.example.sampleapi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleApiController {

    @GetMapping("/user")
    public UserResponse user() {
        return new UserResponse(1L, "Jane Doe", "jane@example.com");
    }

    @GetMapping("/login")
    public LoginResponse login() {
        return new LoginResponse("sample-jwt-token", 3600L);
    }
}
