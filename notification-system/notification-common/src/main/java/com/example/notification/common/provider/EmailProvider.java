package com.example.notification.common.provider;

public interface EmailProvider {

    SendResult send(EmailSendCommand command);
}
