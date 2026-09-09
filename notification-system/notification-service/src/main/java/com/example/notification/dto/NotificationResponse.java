package com.example.notification.dto;

public record NotificationResponse(SendStatus status, String providerMessageId, String error) {

    public static NotificationResponse sent(String providerMessageId) {
        return new NotificationResponse(SendStatus.SENT, providerMessageId, null);
    }

    public static NotificationResponse failed(String error) {
        return new NotificationResponse(SendStatus.FAILED, null, error);
    }
}
