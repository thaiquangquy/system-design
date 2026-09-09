package com.example.notification.provider;

public record EmailSendCommand(String fromEmail, String toEmail, String subject, String content) {}
