package com.example.urlshortener.shorten;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShortenService {

    private final ShortUrlRepository repository;

    /**
     * Returns the base62 short code for the given long URL, reusing an existing code if this
     * long URL was already shortened (idempotency per design.md §6.1).
     */
    @Transactional
    public String shorten(String longUrl) {
        return repository
                .findByLongUrl(longUrl)
                .map(ShortUrl::getShortUrl)
                .orElseGet(() -> createShortCode(longUrl));
    }

    private String createShortCode(String longUrl) {
        ShortUrl entity = repository.save(new ShortUrl(longUrl));
        entity.setShortUrl(Base62Encoder.encode(entity.getId()));
        repository.save(entity);
        return entity.getShortUrl();
    }
}
