package com.example.notificationworker.consumer;

import com.example.notificationworker.messaging.NotificationEvent;
import com.example.notificationworker.provider.SmsProvider;
import com.example.notificationworker.provider.SmsSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsEventConsumer {

    private final SmsProvider smsProvider;

    @KafkaListener(topics = "notification.sms", groupId = "notification-worker")
    public void onMessage(NotificationEvent event) {
        var result = smsProvider.send(new SmsSendCommand(event.phoneNumber(), event.content()));
        if (!result.success()) {
            log.warn("Sms send failed for notification {}: {}", event.notificationId(), result.errorMessage());
        }
    }
}
