package com.example.urlshortener.idgen;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A row in this table carries no data of its own — inserting one is the entire point. Its sole
 * purpose is to hand out a globally unique, monotonically increasing id via the database's
 * auto-increment, decoupled from the short_url table itself, per design.md §10's "DB-based ticket
 * server" decision.
 */
@Entity
@Table(name = "id_ticket")
public class IdTicket {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  public Long getId() {
    return id;
  }
}
