package com.example.notification.messaging;

import com.example.notification.common.messaging.NotificationChannel;
import com.example.notification.common.messaging.NotificationEvent;
import com.example.notification.common.messaging.NotificationTopics;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private static final Map<NotificationChannel, String> TOPIC_BY_CHANNEL = Map.of(
            NotificationChannel.PUSH,
            NotificationTopics.PUSH,
            NotificationChannel.SMS,
            NotificationTopics.SMS,
            NotificationChannel.EMAIL,
            NotificationTopics.EMAIL);

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void send(NotificationEvent event) {
        var topic = TOPIC_BY_CHANNEL.get(event.channel());
        kafkaTemplate.send(topic, event.notificationId().toString(), event);
    }
}
