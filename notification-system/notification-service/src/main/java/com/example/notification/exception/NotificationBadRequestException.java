package com.example.notification.exception;

/** Raised when a recipient cannot be resolved or lacks contact info for the requested channel. */
public class NotificationBadRequestException extends RuntimeException {

    public NotificationBadRequestException(String message) {
        super(message);
    }
}
