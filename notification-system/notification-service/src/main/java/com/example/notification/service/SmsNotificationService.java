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
public class SmsNotificationService implements NotificationService {

    private final UserCacheService userCacheService;
    private final NotificationEventProducer eventProducer;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public NotificationResponse send(NotificationRequest request) {
        var userId = request.firstRecipientUserId();
        var contact = userCacheService.getUserContact(userId);
        if (contact.phone() == null || contact.phone().isBlank()) {
            throw new NotificationBadRequestException("User " + userId + " has no phone number on file");
        }

        var notificationId = UUID.randomUUID();
        var event = new NotificationEvent(
                notificationId,
                channel(),
                userId,
                null,
                request.firstContentValue(),
                null,
                null,
                contact.phone(),
                null);
        eventProducer.send(event);

        return NotificationResponse.queued(notificationId);
    }
}
