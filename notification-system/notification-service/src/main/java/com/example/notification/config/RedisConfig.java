package com.example.notification.config;

import java.time.Duration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        // Default typing embeds a type hint (e.g. "@class") in each cached value so
        // GenericJacksonJsonRedisSerializer#deserialize(byte[]) - which is called without
        // knowing the target type - can rebuild the original class instead of falling back
        // to a LinkedHashMap. Scoped to our own packages + java.util to avoid Jackson's
        // "unsafe" wide-open default typing (arbitrary class deserialization).
        var typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.notification.")
                .allowIfSubType("java.util.")
                .build();

        var serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}
