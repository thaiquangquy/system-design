package com.example.urlshortener.redirect;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

  private final RedirectService redirectService;

  @GetMapping("/{shortUrlCode}")
  public ResponseEntity<Void> redirect(@PathVariable String shortUrlCode) {
    String longUrl = redirectService.resolve(shortUrlCode);
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, longUrl).build();
  }
}
