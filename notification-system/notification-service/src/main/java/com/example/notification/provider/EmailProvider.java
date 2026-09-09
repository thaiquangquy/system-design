package com.example.notification.provider;

public interface EmailProvider {

  SendResult send(EmailSendCommand command);
}
