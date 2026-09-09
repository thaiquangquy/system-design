package com.example.notification.provider;

public interface PushProvider {

    SendResult send(PushSendCommand command);
}
