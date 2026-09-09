package com.example.notification.controller;

import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.service.SmsNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class SmsController {

  private final SmsNotificationService smsNotificationService;

  @PostMapping("/sms")
  public NotificationResponse sendSms(@Valid @RequestBody NotificationRequest request) {
    return smsNotificationService.send(request);
  }
}
