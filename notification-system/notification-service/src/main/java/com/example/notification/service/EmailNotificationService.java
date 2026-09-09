package com.example.notification.service;

import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.provider.EmailProvider;
import com.example.notification.provider.EmailSendCommand;
import com.example.notification.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailNotificationService implements NotificationService {

  private final UserRepository userRepository;
  private final EmailProvider emailProvider;

  @Override
  public NotificationResponse send(NotificationRequest request) {
    var userId = request.firstRecipientUserId();
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotificationBadRequestException("User not found: " + userId));

    if (user.getEmail() == null || user.getEmail().isBlank()) {
      throw new NotificationBadRequestException("User " + userId + " has no email on file");
    }

    var fromEmail = request.from() != null ? request.from().email() : null;
    var command = new EmailSendCommand(fromEmail, user.getEmail(), request.subject(), request.firstContentValue());
    var result = emailProvider.send(command);
    return result.success()
        ? NotificationResponse.sent(result.providerMessageId())
        : NotificationResponse.failed(result.errorMessage());
  }
}
