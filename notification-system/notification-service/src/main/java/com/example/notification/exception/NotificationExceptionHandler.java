package com.example.notification.exception;

import com.example.notification.dto.NotificationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class NotificationExceptionHandler {

  @ExceptionHandler(NotificationBadRequestException.class)
  public ResponseEntity<NotificationResponse> handleBadRequest(NotificationBadRequestException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(NotificationResponse.failed(ex.getMessage()));
  }
}
