package com.example.notificationworker.provider;

public record EmailSendCommand(String fromEmail, String toEmail, String subject, String content) {}
