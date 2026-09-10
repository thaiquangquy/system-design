package com.example.notificationworker.config;

import com.example.notificationworker.messaging.NotificationEvent;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.StringUtils;

/**
 * Consumer key/value deserializers are pinned here in code, not as class-name strings in YAML, so
 * changing the wire format is a compile-checked, IDE-navigable change.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, NotificationEvent> notificationConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        var configs = kafkaProperties.buildConsumerProperties();
        var consumerConnection = connectionDetails.getConsumer();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, consumerConnection.getBootstrapServers());
        if (StringUtils.hasLength(consumerConnection.getSecurityProtocol())) {
            configs.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, consumerConnection.getSecurityProtocol());
        }
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        configs.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        configs.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, NotificationEvent.class);
        configs.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.example.notificationworker.messaging");
        configs.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, NotificationEvent> notificationConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>();
        factory.setConsumerFactory(notificationConsumerFactory);
        return factory;
    }
}
