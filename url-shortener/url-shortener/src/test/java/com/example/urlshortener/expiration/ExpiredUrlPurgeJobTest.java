package com.example.urlshortener.expiration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.shorten.ShortUrlRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExpiredUrlPurgeJobTest {

    @Test
    void deletesRowsExpiredAsOfNow() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        when(repository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(3L);
        ExpiredUrlPurgeJob job = new ExpiredUrlPurgeJob(repository);

        job.purgeExpired();

        verify(repository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
