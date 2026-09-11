package com.example.notificationworker;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.common.domain.Platform;
import com.example.notification.common.messaging.NotificationChannel;
import com.example.notification.common.messaging.NotificationEvent;
import com.example.notification.common.messaging.NotificationTopics;
import com.example.notification.common.provider.PushProvider;
import com.example.notification.common.provider.SendResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/** Publishes a test event straight to the push topic and asserts the (mocked) stub provider was invoked. */
@SpringBootTest
@Testcontainers
class NotificationWorkerFlowIT {

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.1");

    @MockitoBean
    private PushProvider pushProvider;

    private KafkaProducer<String, NotificationEvent> producer;

    @BeforeEach
    void setUp() {
        var props = Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producer = new KafkaProducer<>(props);
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    void consumesPushEventAndCallsProvider() {
        when(pushProvider.send(any())).thenReturn(SendResult.success("stub-push-1"));

        var event = NotificationEvent.builder()
                .notificationId(UUID.randomUUID())
                .channel(NotificationChannel.PUSH)
                .userId(1L)
                .subject("Hello")
                .content("Hi there")
                .devices(List.of(new NotificationEvent.DeviceTarget("device-token-1", Platform.IOS)))
                .build();

        producer.send(new ProducerRecord<>(
                NotificationTopics.PUSH, event.notificationId().toString(), event));
        producer.flush();

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(pushProvider).send(any()));
    }
}
