package com.example.notificationworker.provider;

import com.example.notificationworker.domain.Platform;

public record PushSendCommand(String deviceToken, Platform platform, String title, String body) {}
