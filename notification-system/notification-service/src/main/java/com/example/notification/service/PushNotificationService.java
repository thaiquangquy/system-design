package com.example.notification.service;

import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.provider.PushProvider;
import com.example.notification.provider.PushSendCommand;
import com.example.notification.repository.DeviceRepository;
import com.example.notification.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PushNotificationService implements NotificationService {

  private final UserRepository userRepository;
  private final DeviceRepository deviceRepository;
  private final PushProvider pushProvider;

  @Override
  public NotificationResponse send(NotificationRequest request) {
    var userId = request.firstRecipientUserId();
    userRepository
        .findById(userId)
        .orElseThrow(() -> new NotificationBadRequestException("User not found: " + userId));

    var devices = deviceRepository.findByUserId(userId);
    if (devices.isEmpty()) {
      throw new NotificationBadRequestException("User " + userId + " has no registered devices");
    }

    var body = request.firstContentValue();
    String providerMessageId = null;
    String lastError = null;
    var anySucceeded = false;
    for (var device : devices) {
      var command = new PushSendCommand(device.getToken(), device.getPlatform(), request.subject(), body);
      var result = pushProvider.send(command);
      if (result.success()) {
        anySucceeded = true;
        providerMessageId = result.providerMessageId();
      } else {
        lastError = result.errorMessage();
      }
    }

    return anySucceeded ? NotificationResponse.sent(providerMessageId) : NotificationResponse.failed(lastError);
  }
}
