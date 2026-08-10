package com.example.urlshortener.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.shorten.ShortUrl;
import com.example.urlshortener.shorten.ShortUrlRepository;
import com.example.urlshortener.support.IntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExpiredUrlPurgeJobIT extends IntegrationTestSupport {

    @Autowired
    private ShortUrlRepository repository;

    @Autowired
    private ExpiredUrlPurgeJob purgeJob;

    @Test
    void purgesOnlyExpiredRows() {
        ShortUrl expired = new ShortUrl("expired1", "https://example.com/expired");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        repository.save(expired);

        ShortUrl live = new ShortUrl("live1", "https://example.com/live");
        repository.save(live);

        purgeJob.purgeExpired();

        assertThat(repository.findByShortUrl("expired1")).isEmpty();
        assertThat(repository.findByShortUrl("live1")).isPresent();
    }
}
