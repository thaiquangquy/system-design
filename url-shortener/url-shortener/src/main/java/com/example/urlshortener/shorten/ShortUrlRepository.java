package com.example.urlshortener.shorten;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

  Optional<ShortUrl> findByLongUrl(String longUrl);

  Optional<ShortUrl> findByShortUrl(String shortUrl);

  long deleteByExpiresAtBefore(Instant instant);
}
