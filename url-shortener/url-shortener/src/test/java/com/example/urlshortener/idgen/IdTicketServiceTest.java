package com.example.urlshortener.idgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdTicketServiceTest {

  @Mock private IdTicketRepository repository;
  @Mock private IdTicket saved;
  @InjectMocks private IdTicketService service;

  @Test
  void nextIdReturnsTheGeneratedIdOfTheSavedTicket() {
    when(saved.getId()).thenReturn(42L);
    when(repository.save(any(IdTicket.class))).thenReturn(saved);

    assertThat(service.nextId()).isEqualTo(42L);
  }
}
