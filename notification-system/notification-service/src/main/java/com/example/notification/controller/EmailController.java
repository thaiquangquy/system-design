package com.example.notification.controller;

import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.service.NotificationChannel;
import com.example.notification.service.NotificationServiceRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class EmailController {

    private final NotificationServiceRegistry notificationServiceRegistry;

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse sendEmail(@Valid @RequestBody NotificationRequest request) {
        return notificationServiceRegistry.get(NotificationChannel.EMAIL).send(request);
    }
}
