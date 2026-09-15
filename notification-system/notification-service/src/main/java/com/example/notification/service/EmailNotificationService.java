package com.example.notification.service;

import com.example.notification.cache.UserCacheService;
import com.example.notification.common.messaging.NotificationChannel;
import com.example.notification.common.messaging.NotificationEvent;
import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.exception.NotificationBadRequestException;
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
        var event = NotificationEvent.builder()
                .notificationId(notificationId)
                .channel(channel())
                .userId(userId)
                .subject(request.subject())
                .content(request.firstContentValue())
                .fromEmail(fromEmail)
                .toEmail(contact.email())
                .build();
        eventProducer.send(event);

        return NotificationResponse.queued(notificationId);
    }
}
