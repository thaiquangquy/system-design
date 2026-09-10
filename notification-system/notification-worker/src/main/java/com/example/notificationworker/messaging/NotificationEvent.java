package com.example.notificationworker.messaging;

import com.example.notificationworker.domain.Platform;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Mirrors {@code notification-service}'s producer-side record field-for-field — this is the
 * message shape published to the per-channel Kafka topics. Only the fields relevant to {@code
 * channel} are populated.
 */
@Builder
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
