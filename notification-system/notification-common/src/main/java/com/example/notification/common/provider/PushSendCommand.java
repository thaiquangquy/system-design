package com.example.notification.common.provider;

import com.example.notification.common.domain.Platform;

public record PushSendCommand(String deviceToken, Platform platform, String title, String body) {}
