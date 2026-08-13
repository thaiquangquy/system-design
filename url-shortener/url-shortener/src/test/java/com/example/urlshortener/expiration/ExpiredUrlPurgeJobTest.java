package com.example.urlshortener.expiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.sharding.ShardedShortUrlOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExpiredUrlPurgeJobTest {

    @Test
    void deletesRowsExpiredAsOfNow() {
        ShardedShortUrlOperations shortUrlOperations = mock(ShardedShortUrlOperations.class);
        when(shortUrlOperations.deleteExpired(any(Instant.class))).thenReturn(3L);
        ExpiredUrlPurgeJob job = new ExpiredUrlPurgeJob(shortUrlOperations);

        job.purgeExpired();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(shortUrlOperations).deleteExpired(cutoff.capture());
        assertThat(cutoff.getValue()).isCloseTo(Instant.now(), within(2, ChronoUnit.SECONDS));
    }
}
