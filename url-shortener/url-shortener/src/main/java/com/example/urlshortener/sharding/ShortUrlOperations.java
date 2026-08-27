package com.example.urlshortener.sharding;

import com.example.urlshortener.shorten.ShortUrl;
import java.time.Instant;
import java.util.Optional;

/**
 * Port through which the write/read/purge flows access {@link ShortUrl} storage, independent of
 * whether sharding is enabled. {@link ShardedShortUrlOperations} is the only implementation today,
 * but callers depend on this interface (not the concrete class) per SOLID/DIP.
 */
public interface ShortUrlOperations {

  ShortUrl save(ShortUrl entity);

  Optional<ShortUrl> findByShortUrl(String shortUrlCode);

  Optional<ShortUrl> findByLongUrl(String longUrl);

  long deleteExpired(Instant cutoff);
}
