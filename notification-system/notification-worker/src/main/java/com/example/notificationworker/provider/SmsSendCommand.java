package com.example.notificationworker.provider;

public record SmsSendCommand(String phoneNumber, String content) {}
