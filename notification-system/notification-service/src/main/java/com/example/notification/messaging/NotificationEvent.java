package com.example.notification.messaging;

import com.example.notification.domain.Platform;
import com.example.notification.service.NotificationChannel;
import java.util.List;
import java.util.UUID;

/**
 * Message published to the per-channel queue at intake time. Carries the recipient contact
 * details resolved from the cache/DB so the worker can call the provider without its own DB
 * dependency. Only the fields relevant to {@code channel} are populated.
 */
public record NotificationEvent(
        UUID notificationId,
        NotificationChannel channel,
        Long userId,
        String subject,
        String content,
        String fromEmail,
        String toEmail,
        String phoneNumber,
        List<DeviceTarget> devices) {

    public record DeviceTarget(String token, Platform platform) {}
}
