package com.example.urlshortener.shorten;

import com.example.urlshortener.shorten.dto.ShortenRequest;
import com.example.urlshortener.shorten.dto.ShortenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShortenController {

    private final ShortenService shortenService;

    @Value("${urlshortener.base-url}")
    private String baseUrl;

    @PostMapping("/api/v1/shorten")
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request) {
        String code = shortenService.shorten(request.longUrl());
        return new ShortenResponse(baseUrl + "/" + code);
    }
}
