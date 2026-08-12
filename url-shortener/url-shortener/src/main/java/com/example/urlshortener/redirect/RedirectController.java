package com.example.urlshortener.redirect;

import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.shorten.ShortUrlRepository;
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

    private final ShortUrlRepository repository;

    @GetMapping("/{shortUrlCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortUrlCode) {
        String longUrl =
                repository
                        .findByShortUrl(shortUrlCode)
                        .orElseThrow(() -> new ShortUrlNotFoundException(shortUrlCode))
                        .getLongUrl();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, longUrl).build();
    }
}
