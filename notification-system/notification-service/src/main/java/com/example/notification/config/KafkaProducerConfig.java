package com.example.notification.config;

import com.example.notification.messaging.NotificationEvent;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.StringUtils;

/**
 * Producer key/value serializers are pinned here in code, not as class-name strings in YAML, so
 * changing the wire format is a compile-checked, IDE-navigable change.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, NotificationEvent> notificationProducerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        var configs = kafkaProperties.buildProducerProperties();
        var producerConnection = connectionDetails.getProducer();
        configs.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, producerConnection.getBootstrapServers());
        if (StringUtils.hasLength(producerConnection.getSecurityProtocol())) {
            configs.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, producerConnection.getSecurityProtocol());
        }
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configs);
    }

    @Bean
    public KafkaTemplate<String, NotificationEvent> kafkaTemplate(
            ProducerFactory<String, NotificationEvent> notificationProducerFactory) {
        return new KafkaTemplate<>(notificationProducerFactory);
    }
}
