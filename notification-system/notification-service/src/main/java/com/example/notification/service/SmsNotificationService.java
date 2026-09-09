package com.example.notification.service;

import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.provider.SmsProvider;
import com.example.notification.provider.SmsSendCommand;
import com.example.notification.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsNotificationService implements NotificationService {

  private final UserRepository userRepository;
  private final SmsProvider smsProvider;

  @Override
  public NotificationResponse send(NotificationRequest request) {
    var userId = request.firstRecipientUserId();
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotificationBadRequestException("User not found: " + userId));

    if (user.getPhone() == null || user.getPhone().isBlank()) {
      throw new NotificationBadRequestException("User " + userId + " has no phone number on file");
    }

    var result = smsProvider.send(new SmsSendCommand(user.getPhone(), request.firstContentValue()));
    return result.success()
        ? NotificationResponse.sent(result.providerMessageId())
        : NotificationResponse.failed(result.errorMessage());
  }
}
