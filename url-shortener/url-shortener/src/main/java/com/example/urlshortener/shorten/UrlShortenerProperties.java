package com.example.urlshortener.shorten;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds urlshortener.* top-level config — currently just the public base URL for short links. */
@ConfigurationProperties(prefix = "urlshortener")
public record UrlShortenerProperties(String baseUrl) {}
