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
public class EmailNotificationService implements NotificationService {

    private final UserCacheService userCacheService;
    private final NotificationEventProducer eventProducer;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public NotificationResponse send(NotificationRequest request) {
        var userId = request.firstRecipientUserId();
        var contact = userCacheService.getUserContact(userId);
        if (contact.email() == null || contact.email().isBlank()) {
            throw new NotificationBadRequestException("User " + userId + " has no valid email");
        }

        var fromEmail = request.from() != null ? request.from().email() : null;
        var notificationId = UUID.randomUUID();
        var event = new NotificationEvent(
                notificationId,
                channel(),
                userId,
                request.subject(),
                request.firstContentValue(),
                fromEmail,
                contact.email(),
                null,
                null);
        eventProducer.send(event);

        return NotificationResponse.queued(notificationId);
    }
}
