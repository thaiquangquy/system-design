package com.example.notification.common.provider;

public interface PushProvider {

    SendResult send(PushSendCommand command);
}
