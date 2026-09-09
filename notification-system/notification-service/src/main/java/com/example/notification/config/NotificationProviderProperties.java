package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds notification.provider.* — endpoint/credential config per channel provider. */
@ConfigurationProperties(prefix = "notification.provider")
public record NotificationProviderProperties(Push push, Sms sms, Email email) {

  public record Push(String endpoint, String apiKey) {}

  public record Sms(String endpoint, String apiKey) {}

  public record Email(String endpoint, String apiKey) {}
}
