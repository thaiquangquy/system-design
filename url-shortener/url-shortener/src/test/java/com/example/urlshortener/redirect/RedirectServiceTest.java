package com.example.urlshortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.cache.RedirectCacheService;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.sharding.ShortUrlOperations;
import com.example.urlshortener.shorten.ShortUrl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

  @Mock private ShortUrlOperations shortUrlOperations;
  @Mock private RedirectCacheService cache;
  @InjectMocks private RedirectService service;

  @Test
  void returnsCachedLongUrlWithoutHittingTheRepository() {
    when(cache.get("abc")).thenReturn(Optional.of("https://example.com/cached"));

    String longUrl = service.resolve("abc");

    assertThat(longUrl).isEqualTo("https://example.com/cached");
    verify(shortUrlOperations, never()).findByShortUrl("abc");
  }

  @Test
  void loadsFromRepositoryAndPopulatesCacheOnMiss() {
    when(cache.get("abc")).thenReturn(Optional.empty());
    when(shortUrlOperations.findByShortUrl("abc"))
        .thenReturn(Optional.of(new ShortUrl("abc", "https://example.com/db")));

    String longUrl = service.resolve("abc");

    assertThat(longUrl).isEqualTo("https://example.com/db");
    verify(cache).put("abc", "https://example.com/db");
  }

  @Test
  void throwsWhenCodeIsUnknownEverywhere() {
    when(cache.get("missing")).thenReturn(Optional.empty());
    when(shortUrlOperations.findByShortUrl("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resolve("missing"))
        .isInstanceOf(ShortUrlNotFoundException.class);
  }
}
