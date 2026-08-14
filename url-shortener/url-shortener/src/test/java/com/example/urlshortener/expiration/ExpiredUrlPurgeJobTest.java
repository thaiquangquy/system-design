package com.example.urlshortener.expiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.sharding.ShortUrlOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiredUrlPurgeJobTest {

  @Mock private ShortUrlOperations shortUrlOperations;
  @InjectMocks private ExpiredUrlPurgeJob job;

  @Test
  void deletesRowsExpiredAsOfNow() {
    when(shortUrlOperations.deleteExpired(any(Instant.class))).thenReturn(3L);

    job.purgeExpired();

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(shortUrlOperations).deleteExpired(cutoff.capture());
    assertThat(cutoff.getValue()).isCloseTo(Instant.now(), within(2, ChronoUnit.SECONDS));
  }
}
