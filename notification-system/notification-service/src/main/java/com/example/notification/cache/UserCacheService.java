package com.example.notification.cache;

import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.repository.DeviceRepository;
import com.example.notification.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Cache-aside (Redis -> Postgres on miss) lookup of the contact details needed to send. */
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    @Cacheable(cacheNames = "user-contacts", key = "#userId")
    public UserContact getUserContact(Long userId) {
        var user = userRepository
                .findById(userId)
                .orElseThrow(() -> new NotificationBadRequestException("User not found: " + userId));
        var devices = deviceRepository.findByUserId(userId).stream()
                .map(device -> new UserContact.Device(device.getToken(), device.getPlatform()))
                .toList();
        return new UserContact(userId, user.getEmail(), user.getPhone(), devices);
    }
}
