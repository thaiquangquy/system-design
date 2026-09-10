package com.example.notification.messaging;

import com.example.notification.service.NotificationChannel;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    public static final String PUSH_TOPIC = "notification.push";
    public static final String SMS_TOPIC = "notification.sms";
    public static final String EMAIL_TOPIC = "notification.email";

    private static final Map<NotificationChannel, String> TOPIC_BY_CHANNEL = Map.of(
            NotificationChannel.PUSH,
            PUSH_TOPIC,
            NotificationChannel.SMS,
            SMS_TOPIC,
            NotificationChannel.EMAIL,
            EMAIL_TOPIC);

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void send(NotificationEvent event) {
        var topic = TOPIC_BY_CHANNEL.get(event.channel());
        kafkaTemplate.send(topic, event.notificationId().toString(), event);
    }
}
