package com.example.notification.provider;

public record SendResult(boolean success, String providerMessageId, String errorMessage) {

  public static SendResult success(String providerMessageId) {
    return new SendResult(true, providerMessageId, null);
  }

  public static SendResult failure(String errorMessage) {
    return new SendResult(false, null, errorMessage);
  }
}
