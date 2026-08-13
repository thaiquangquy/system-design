package com.example.urlshortener.shorten;

import com.example.urlshortener.shorten.dto.ShortenRequest;
import com.example.urlshortener.shorten.dto.ShortenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShortenController {

  private final ShortenService shortenService;
  private final UrlShortenerProperties properties;

  @PostMapping("/api/v1/shorten")
  public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request) {
    String code = shortenService.shorten(request.longUrl());
    return new ShortenResponse(properties.baseUrl() + "/" + code);
  }
}
