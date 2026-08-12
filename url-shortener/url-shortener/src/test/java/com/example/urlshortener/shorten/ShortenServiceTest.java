package com.example.urlshortener.shorten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShortenServiceTest {

    @Test
    void returnsExistingShortCodeWhenLongUrlAlreadyKnown() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        ShortUrl existing = new ShortUrl("https://example.com/already-shortened");
        existing.setId(1L);
        existing.setShortUrl("1");
        when(repository.findByLongUrl("https://example.com/already-shortened")).thenReturn(Optional.of(existing));
        ShortenService service = new ShortenService(repository);

        String code = service.shorten("https://example.com/already-shortened");

        assertThat(code).isEqualTo("1");
        verify(repository, never()).save(any());
    }

    @Test
    void createsAndEncodesShortCodeForNewLongUrl() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        when(repository.findByLongUrl("https://example.com/new")).thenReturn(Optional.empty());
        when(repository.save(any(ShortUrl.class)))
                .thenAnswer(
                        invocation -> {
                            ShortUrl arg = invocation.getArgument(0);
                            if (arg.getId() == null) {
                                arg.setId(62L);
                            }
                            return arg;
                        });
        ShortenService service = new ShortenService(repository);

        String code = service.shorten("https://example.com/new");

        assertThat(code).isEqualTo(Base62Encoder.encode(62L));
        verify(repository, times(2)).save(any(ShortUrl.class));
    }
}
