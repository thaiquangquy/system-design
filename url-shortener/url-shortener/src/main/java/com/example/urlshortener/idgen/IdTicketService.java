package com.example.urlshortener.idgen;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdTicketService {

  private final IdTicketRepository repository;

  /**
   * Issues a new globally unique id. Runs in its own transaction (REQUIRES_NEW) so a ticket is
   * never rolled back alongside the caller's short_url insert — an id being "wasted" on a failed
   * shorten attempt is harmless (base62 length only grows with the id's magnitude), but reusing an
   * id across two concurrent attempts would not be.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long nextId() {
    return repository.save(new IdTicket()).getId();
  }
}
