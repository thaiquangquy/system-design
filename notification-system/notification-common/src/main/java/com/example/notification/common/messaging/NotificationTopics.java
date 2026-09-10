package com.example.notification.common.messaging;

/** Kafka topic names shared by the notification-service producer and notification-worker consumers. */
public final class NotificationTopics {

    public static final String PUSH_TOPIC = "notification.push";
    public static final String SMS_TOPIC = "notification.sms";
    public static final String EMAIL_TOPIC = "notification.email";

    private NotificationTopics() {}
}
