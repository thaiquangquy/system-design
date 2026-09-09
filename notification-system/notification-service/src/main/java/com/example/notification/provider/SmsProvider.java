package com.example.notification.provider;

public interface SmsProvider {

  SendResult send(SmsSendCommand command);
}
