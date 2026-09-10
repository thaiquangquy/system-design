package com.example.notification.dto;

import java.util.UUID;

public record NotificationResponse(SendStatus status, String notificationId, String error) {

    public static NotificationResponse queued(UUID notificationId) {
        return new NotificationResponse(SendStatus.QUEUED, notificationId.toString(), null);
    }

    public static NotificationResponse failed(String error) {
        return new NotificationResponse(SendStatus.FAILED, null, error);
    }
}
