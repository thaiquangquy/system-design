package com.example.urlshortener.shorten;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "short_url",
    indexes = {
      @Index(name = "idx_short_url_code", columnList = "short_url", unique = true),
      @Index(name = "idx_short_url_long_url", columnList = "long_url"),
      @Index(name = "idx_short_url_expires_at", columnList = "expires_at")
    })
@Getter
@Setter
@NoArgsConstructor
public class ShortUrl {

  /** Default TTL from creation, per design.md §4/§10. */
  public static final Duration DEFAULT_TTL = Duration.ofDays(365);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "short_url", nullable = false, length = 16)
  private String shortUrl;

  @Column(name = "long_url", nullable = false, columnDefinition = "text")
  private String longUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public ShortUrl(String shortUrl, String longUrl) {
    this.shortUrl = shortUrl;
    this.longUrl = longUrl;
    this.createdAt = Instant.now();
    this.expiresAt = this.createdAt.plus(DEFAULT_TTL);
  }
}
