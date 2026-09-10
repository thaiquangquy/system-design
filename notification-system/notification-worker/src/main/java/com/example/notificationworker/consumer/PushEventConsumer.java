package com.example.notificationworker.consumer;

import com.example.notification.common.messaging.NotificationEvent;
import com.example.notification.common.messaging.NotificationTopics;
import com.example.notification.common.provider.PushProvider;
import com.example.notification.common.provider.PushSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PushEventConsumer {

    private final PushProvider pushProvider;

    @KafkaListener(topics = NotificationTopics.PUSH_TOPIC, groupId = "notification-worker")
    public void onMessage(NotificationEvent event) {
        for (var device : event.devices()) {
            var command = new PushSendCommand(device.token(), device.platform(), event.subject(), event.content());
            var result = pushProvider.send(command);
            if (!result.success()) {
                log.warn("Push send failed for notification {}: {}", event.notificationId(), result.errorMessage());
            }
        }
    }
}
