package com.example.notification.common.provider;

public record SmsSendCommand(String phoneNumber, String content) {}
