package com.example.urlshortener.shorten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.idgen.IdTicketService;
import com.example.urlshortener.sharding.ShortUrlOperations;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShortenServiceTest {

  @Mock private ShortUrlOperations shortUrlOperations;
  @Mock private IdTicketService idTicketService;
  @InjectMocks private ShortenService service;

  @Test
  void returnsExistingShortCodeWhenLongUrlAlreadyKnown() {
    ShortUrl existing = new ShortUrl("1", "https://example.com/already-shortened");
    when(shortUrlOperations.findByLongUrl("https://example.com/already-shortened"))
        .thenReturn(Optional.of(existing));

    String code = service.shorten("https://example.com/already-shortened");

    assertThat(code).isEqualTo("1");
    verify(shortUrlOperations, never()).save(any());
    verify(idTicketService, never()).nextId();
  }

  @Test
  void createsAndEncodesShortCodeForNewLongUrl() {
    when(shortUrlOperations.findByLongUrl("https://example.com/new")).thenReturn(Optional.empty());
    when(idTicketService.nextId()).thenReturn(62L);
    when(shortUrlOperations.save(any(ShortUrl.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String code = service.shorten("https://example.com/new");

    assertThat(code).isEqualTo(Base62Encoder.encode(62L));
    verify(shortUrlOperations, times(1)).save(any(ShortUrl.class));
  }
}
