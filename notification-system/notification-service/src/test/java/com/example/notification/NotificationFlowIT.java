package com.example.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.notification.common.domain.Platform;
import com.example.notification.common.messaging.NotificationTopics;
import com.example.notification.domain.Device;
import com.example.notification.domain.User;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.dto.SendStatus;
import com.example.notification.repository.DeviceRepository;
import com.example.notification.repository.UserRepository;
import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * End-to-end: produce (via the API) -> land on the correct Kafka topic, and cache-aside verified
 * against real Redis (second lookup for the same user does not hit Postgres again).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class NotificationFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer("redis:7-alpine");

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.1");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private User user;
    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("user@example.com", "+15551234567"));
        deviceRepository.save(new Device(user, Platform.IOS, "device-token-1"));

        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(
                List.of(NotificationTopics.PUSH, NotificationTopics.SMS, NotificationTopics.EMAIL));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void sendsPushAndPublishesEventToPushTopic() {
        var response = post("/v1/notifications/push", pushBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().status()).isEqualTo(SendStatus.QUEUED);
        assertThat(response.getBody().notificationId()).isNotBlank();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var records = consumer.poll(Duration.ofMillis(500));
            assertThat(records.records(NotificationTopics.PUSH))
                    .anySatisfy(record -> assertThat(record.value())
                            .contains(response.getBody().notificationId()));
        });
    }

    @Test
    void cacheAsideHitsPostgresOnceForRepeatedLookups() {
        post("/v1/notifications/push", pushBody());
        post("/v1/notifications/push", pushBody());

        verify(userRepository, times(1)).findById(user.getId());
    }

    private ResponseEntity<NotificationResponse> post(String path, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                "http://localhost:" + port + path, new HttpEntity<>(body, headers), NotificationResponse.class);
    }

    private String pushBody() {
        return """
        {
          "to": [{"user_id": %d}],
          "subject": "Hello",
          "content": [{"type": "text/plain", "value": "Hi there"}]
        }
        """.formatted(user.getId());
    }
}
