package com.example.urlshortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.cache.RedirectCacheService;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.sharding.ShardedShortUrlOperations;
import com.example.urlshortener.shorten.ShortUrl;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RedirectServiceTest {

    @Test
    void returnsCachedLongUrlWithoutHittingTheRepository() {
        ShardedShortUrlOperations shortUrlOperations = mock(ShardedShortUrlOperations.class);
        RedirectCacheService cache = mock(RedirectCacheService.class);
        when(cache.get("abc")).thenReturn(Optional.of("https://example.com/cached"));
        RedirectService service = new RedirectService(shortUrlOperations, cache);

        String longUrl = service.resolve("abc");

        assertThat(longUrl).isEqualTo("https://example.com/cached");
        verify(shortUrlOperations, never()).findByShortUrl("abc");
    }

    @Test
    void loadsFromRepositoryAndPopulatesCacheOnMiss() {
        ShardedShortUrlOperations shortUrlOperations = mock(ShardedShortUrlOperations.class);
        RedirectCacheService cache = mock(RedirectCacheService.class);
        when(cache.get("abc")).thenReturn(Optional.empty());
        when(shortUrlOperations.findByShortUrl("abc"))
                .thenReturn(Optional.of(new ShortUrl("abc", "https://example.com/db")));
        RedirectService service = new RedirectService(shortUrlOperations, cache);

        String longUrl = service.resolve("abc");

        assertThat(longUrl).isEqualTo("https://example.com/db");
        verify(cache).put("abc", "https://example.com/db");
    }

    @Test
    void throwsWhenCodeIsUnknownEverywhere() {
        ShardedShortUrlOperations shortUrlOperations = mock(ShardedShortUrlOperations.class);
        RedirectCacheService cache = mock(RedirectCacheService.class);
        when(cache.get("missing")).thenReturn(Optional.empty());
        when(shortUrlOperations.findByShortUrl("missing")).thenReturn(Optional.empty());
        RedirectService service = new RedirectService(shortUrlOperations, cache);

        assertThatThrownBy(() -> service.resolve("missing")).isInstanceOf(ShortUrlNotFoundException.class);
    }
}
