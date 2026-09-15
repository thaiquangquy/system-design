package com.example.notification.common.messaging;

/** Kafka topic names shared by the notification-service producer and notification-worker consumers. */
public final class NotificationTopics {

    public static final String PUSH = "notification.push";
    public static final String SMS = "notification.sms";
    public static final String EMAIL = "notification.email";

    private NotificationTopics() {}
}
