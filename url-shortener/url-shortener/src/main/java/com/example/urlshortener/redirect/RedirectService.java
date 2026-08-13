package com.example.urlshortener.redirect;

import com.example.urlshortener.cache.RedirectCacheService;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.sharding.ShortUrlOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedirectService {

  private final ShortUrlOperations shortUrlOperations;
  private final RedirectCacheService cache;

  public String resolve(String shortUrlCode) {
    return cache.get(shortUrlCode).orElseGet(() -> loadAndCache(shortUrlCode));
  }

  private String loadAndCache(String shortUrlCode) {
    String longUrl =
        shortUrlOperations
            .findByShortUrl(shortUrlCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortUrlCode))
            .getLongUrl();
    cache.put(shortUrlCode, longUrl);
    return longUrl;
  }
}
