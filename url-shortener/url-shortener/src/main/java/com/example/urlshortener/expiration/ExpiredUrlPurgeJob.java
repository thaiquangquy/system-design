package com.example.urlshortener.expiration;

import com.example.urlshortener.sharding.ShardedShortUrlOperations;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Purges short URLs past their expiresAt, per design.md §4/§8. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredUrlPurgeJob {

    private final ShardedShortUrlOperations shortUrlOperations;

    @Scheduled(cron = "${urlshortener.expiration.purge-cron}")
    public void purgeExpired() {
        long deleted = shortUrlOperations.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired short URL(s)", deleted);
        }
    }
}
