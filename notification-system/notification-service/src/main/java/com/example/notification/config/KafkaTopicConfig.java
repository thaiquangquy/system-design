package com.example.notification.config;

import com.example.notification.messaging.NotificationEventProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic pushTopic() {
        return TopicBuilder.name(NotificationEventProducer.PUSH_TOPIC).build();
    }

    @Bean
    public NewTopic smsTopic() {
        return TopicBuilder.name(NotificationEventProducer.SMS_TOPIC).build();
    }

    @Bean
    public NewTopic emailTopic() {
        return TopicBuilder.name(NotificationEventProducer.EMAIL_TOPIC).build();
    }
}
