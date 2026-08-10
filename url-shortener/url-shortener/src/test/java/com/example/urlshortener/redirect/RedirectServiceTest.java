package com.example.urlshortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.cache.RedirectCacheService;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.shorten.ShortUrl;
import com.example.urlshortener.shorten.ShortUrlRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RedirectServiceTest {

    @Test
    void returnsCachedLongUrlWithoutHittingTheRepository() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        RedirectCacheService cache = mock(RedirectCacheService.class);
        when(cache.get("abc")).thenReturn(Optional.of("https://example.com/cached"));
        RedirectService service = new RedirectService(repository, cache);

        String longUrl = service.resolve("abc");

        assertThat(longUrl).isEqualTo("https://example.com/cached");
        verify(repository, never()).findByShortUrl("abc");
    }

    @Test
    void loadsFromRepositoryAndPopulatesCacheOnMiss() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        RedirectCacheService cache = mock(RedirectCacheService.class);
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(repository.findByShortUrl("abc")).thenReturn(Optional.of(new ShortUrl("abc", "https://example.com/db")));
        RedirectService service = new RedirectService(repository, cache);

        String longUrl = service.resolve("abc");

        assertThat(longUrl).isEqualTo("https://example.com/db");
        verify(cache).put("abc", "https://example.com/db");
    }

    @Test
    void throwsWhenCodeIsUnknownEverywhere() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        RedirectCacheService cache = mock(RedirectCacheService.class);
        when(cache.get("missing")).thenReturn(Optional.empty());
        when(repository.findByShortUrl("missing")).thenReturn(Optional.empty());
        RedirectService service = new RedirectService(repository, cache);

        assertThatThrownBy(() -> service.resolve("missing")).isInstanceOf(ShortUrlNotFoundException.class);
    }
}
