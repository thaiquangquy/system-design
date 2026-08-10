package com.example.urlshortener.expiration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.sharding.ShardedShortUrlOperations;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExpiredUrlPurgeJobTest {

    @Test
    void deletesRowsExpiredAsOfNow() {
        ShardedShortUrlOperations shortUrlOperations = mock(ShardedShortUrlOperations.class);
        when(shortUrlOperations.deleteExpired(any(Instant.class))).thenReturn(3L);
        ExpiredUrlPurgeJob job = new ExpiredUrlPurgeJob(shortUrlOperations);

        job.purgeExpired();

        verify(shortUrlOperations).deleteExpired(any(Instant.class));
    }
}
