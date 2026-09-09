package com.example.notification.provider;

import com.example.notification.domain.Platform;

public record PushSendCommand(String deviceToken, Platform platform, String title, String body) {}
