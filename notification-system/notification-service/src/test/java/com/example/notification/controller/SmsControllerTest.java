package com.example.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.notification.dto.NotificationResponse;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.service.NotificationChannel;
import com.example.notification.service.NotificationService;
import com.example.notification.service.NotificationServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SmsController.class)
class SmsControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private NotificationServiceRegistry notificationServiceRegistry;
  private NotificationService smsNotificationService;

  @BeforeEach
  void setUp() {
    smsNotificationService = mock(NotificationService.class);
    when(notificationServiceRegistry.get(NotificationChannel.SMS)).thenReturn(smsNotificationService);
  }

  @Test
  void returnsSentResponseWhenServiceSucceeds() throws Exception {
    when(smsNotificationService.send(any())).thenReturn(NotificationResponse.sent("msg-1"));

    mockMvc
        .perform(
            post("/v1/notifications/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "to": [{"user_id": 1}],
                      "content": [{"type": "text/plain", "value": "Hi there"}]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.provider_message_id").value("msg-1"));
  }

  @Test
  void returnsBadRequestWhenServiceRejectsRequest() throws Exception {
    when(smsNotificationService.send(any()))
        .thenThrow(new NotificationBadRequestException("User 1 has no phone number on file"));

    mockMvc
        .perform(
            post("/v1/notifications/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "to": [{"user_id": 1}],
                      "content": [{"type": "text/plain", "value": "Hi there"}]
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.error").value("User 1 has no phone number on file"));
  }
}
