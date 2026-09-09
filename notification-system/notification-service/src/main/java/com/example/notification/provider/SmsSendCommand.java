package com.example.notification.provider;

public record SmsSendCommand(String phoneNumber, String content) {}
