package com.example.notification.controller;

import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.service.EmailNotificationService;
import com.example.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
public class EmailController {
    private final NotificationService emailNotificationService;

    public EmailController(@Qualifier("emailNotificationService") NotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @PostMapping("/email")
    public NotificationResponse sendEmail(@Valid @RequestBody NotificationRequest request) {
        return emailNotificationService.send(request);
    }
}
