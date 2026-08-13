package com.example.urlshortener.idgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class IdTicketServiceTest {

  @Test
  void nextIdReturnsTheGeneratedIdOfTheSavedTicket() {
    IdTicketRepository repository = mock(IdTicketRepository.class);
    IdTicket saved = mock(IdTicket.class);
    when(saved.getId()).thenReturn(42L);
    when(repository.save(any(IdTicket.class))).thenReturn(saved);
    IdTicketService service = new IdTicketService(repository);

    assertThat(service.nextId()).isEqualTo(42L);
  }
}
