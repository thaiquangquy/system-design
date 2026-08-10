package com.example.urlshortener.shorten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.idgen.IdTicketService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShortenServiceTest {

    @Test
    void returnsExistingShortCodeWhenLongUrlAlreadyKnown() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        IdTicketService idTicketService = mock(IdTicketService.class);
        ShortUrl existing = new ShortUrl("1", "https://example.com/already-shortened");
        existing.setId(1L);
        when(repository.findByLongUrl("https://example.com/already-shortened")).thenReturn(Optional.of(existing));
        ShortenService service = new ShortenService(repository, idTicketService);

        String code = service.shorten("https://example.com/already-shortened");

        assertThat(code).isEqualTo("1");
        verify(repository, never()).save(any());
        verify(idTicketService, never()).nextId();
    }

    @Test
    void createsAndEncodesShortCodeForNewLongUrl() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        IdTicketService idTicketService = mock(IdTicketService.class);
        when(repository.findByLongUrl("https://example.com/new")).thenReturn(Optional.empty());
        when(idTicketService.nextId()).thenReturn(62L);
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ShortenService service = new ShortenService(repository, idTicketService);

        String code = service.shorten("https://example.com/new");

        assertThat(code).isEqualTo(Base62Encoder.encode(62L));
        verify(repository, times(1)).save(any(ShortUrl.class));
    }
}
