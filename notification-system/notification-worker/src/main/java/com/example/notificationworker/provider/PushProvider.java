package com.example.notificationworker.provider;

public interface PushProvider {

    SendResult send(PushSendCommand command);
}
