package com.example.notification.service;

import com.example.notification.common.messaging.NotificationChannel;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class NotificationServiceRegistry {

    private final Map<NotificationChannel, NotificationService> servicesByChannel;

    public NotificationServiceRegistry(List<NotificationService> services) {
        this.servicesByChannel = services.stream()
                .collect(Collectors.toUnmodifiableMap(NotificationService::channel, Function.identity()));
    }

    public NotificationService get(NotificationChannel channel) {
        var service = servicesByChannel.get(channel);
        if (service == null) {
            throw new IllegalStateException("No NotificationService registered for channel: " + channel);
        }
        return service;
    }
}
