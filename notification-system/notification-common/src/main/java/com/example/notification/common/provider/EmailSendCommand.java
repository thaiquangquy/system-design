package com.example.notification.common.provider;

public record EmailSendCommand(String fromEmail, String toEmail, String subject, String content) {}
