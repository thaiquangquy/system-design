package com.example.notification.service;

import com.example.notification.cache.UserCacheService;
import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.messaging.NotificationEvent;
import com.example.notification.messaging.NotificationEventProducer;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PushNotificationService implements NotificationService {

    private final UserCacheService userCacheService;
    private final NotificationEventProducer eventProducer;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public NotificationResponse send(NotificationRequest request) {
        var userId = request.firstRecipientUserId();
        var contact = userCacheService.getUserContact(userId);
        if (contact.devices().isEmpty()) {
            throw new NotificationBadRequestException("User " + userId + " has no registered devices");
        }

        var devices = contact.devices().stream()
                .map(device -> new NotificationEvent.DeviceTarget(device.token(), device.platform()))
                .toList();
        var notificationId = UUID.randomUUID();
        var event = new NotificationEvent(
                notificationId,
                channel(),
                userId,
                request.subject(),
                request.firstContentValue(),
                null,
                null,
                null,
                devices);
        eventProducer.send(event);

        return NotificationResponse.queued(notificationId);
    }
}
