package com.example.notification.service;

import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.NotificationResponse;

public interface NotificationService {

    NotificationChannel channel();

    NotificationResponse send(NotificationRequest request);
}
