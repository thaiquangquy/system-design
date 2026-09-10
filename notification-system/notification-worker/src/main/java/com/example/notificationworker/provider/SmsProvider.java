package com.example.notificationworker.provider;

public interface SmsProvider {

    SendResult send(SmsSendCommand command);
}
