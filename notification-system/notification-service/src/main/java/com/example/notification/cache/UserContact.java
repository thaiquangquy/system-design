package com.example.notification.cache;

import com.example.notification.domain.Platform;
import java.util.List;

/** Cache-aside projection of the user/device data a {@code NotificationService} needs to send. */
public record UserContact(Long userId, String email, String phone, List<Device> devices) {

    public record Device(String token, Platform platform) {}
}
