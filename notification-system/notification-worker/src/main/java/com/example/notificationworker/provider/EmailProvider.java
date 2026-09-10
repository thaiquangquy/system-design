package com.example.notificationworker.provider;

public interface EmailProvider {

    SendResult send(EmailSendCommand command);
}
