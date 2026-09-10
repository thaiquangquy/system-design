package com.example.notification.common.provider;

public interface SmsProvider {

    SendResult send(SmsSendCommand command);
}
