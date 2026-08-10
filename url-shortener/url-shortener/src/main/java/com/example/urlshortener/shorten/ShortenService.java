package com.example.urlshortener.shorten;

import com.example.urlshortener.idgen.IdTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShortenService {

    private final ShortUrlRepository repository;
    private final IdTicketService idTicketService;

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
        String code = Base62Encoder.encode(idTicketService.nextId());
        repository.save(new ShortUrl(code, longUrl));
        return code;
    }
}
