package com.example.urlshortener.shorten;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "short_url",
        indexes = {
            @Index(name = "idx_short_url_code", columnList = "short_url", unique = true),
            @Index(name = "idx_short_url_long_url", columnList = "long_url")
        })
@Getter
@Setter
@NoArgsConstructor
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url", nullable = false, length = 16)
    private String shortUrl;

    @Column(name = "long_url", nullable = false, columnDefinition = "text")
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ShortUrl(String shortUrl, String longUrl) {
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = Instant.now();
    }
}
