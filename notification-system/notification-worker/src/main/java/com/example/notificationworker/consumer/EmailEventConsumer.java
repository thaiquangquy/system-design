package com.example.notificationworker.consumer;

import com.example.notification.common.messaging.NotificationEvent;
import com.example.notification.common.messaging.NotificationTopics;
import com.example.notification.common.provider.EmailProvider;
import com.example.notification.common.provider.EmailSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventConsumer {

    private final EmailProvider emailProvider;

    @KafkaListener(topics = NotificationTopics.EMAIL_TOPIC, groupId = "notification-worker")
    public void onMessage(NotificationEvent event) {
        var command = new EmailSendCommand(event.fromEmail(), event.toEmail(), event.subject(), event.content());
        var result = emailProvider.send(command);
        if (!result.success()) {
            log.warn("Email send failed for notification {}: {}", event.notificationId(), result.errorMessage());
        }
    }
}
