package com.example.urlshortener.shorten;

import com.example.urlshortener.idgen.IdTicketService;
import com.example.urlshortener.sharding.ShortUrlOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShortenService {

  private final ShortUrlOperations shortUrlOperations;
  private final IdTicketService idTicketService;

  /**
   * Returns the base62 short code for the given long URL, reusing an existing code if this long URL
   * was already shortened (idempotency per design.md §6.1). Not wrapped in one transaction — see
   * {@link ShortUrlOperations} for why each call is its own.
   */
  public String shorten(String longUrl) {
    return shortUrlOperations
        .findByLongUrl(longUrl)
        .map(ShortUrl::getShortUrl)
        .orElseGet(() -> createShortCode(longUrl));
  }

  private String createShortCode(String longUrl) {
    String code = Base62Encoder.encode(idTicketService.nextId());
    shortUrlOperations.save(new ShortUrl(code, longUrl));
    return code;
  }
}
