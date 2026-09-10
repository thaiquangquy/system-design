package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.cache.UserCacheService;
import com.example.notification.cache.UserContact;
import com.example.notification.common.messaging.NotificationChannel;
import com.example.notification.common.messaging.NotificationEvent;
import com.example.notification.dto.ContentPart;
import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.RecipientRef;
import com.example.notification.dto.SendStatus;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.messaging.NotificationEventProducer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmsNotificationServiceTest {

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private NotificationEventProducer eventProducer;

    @InjectMocks
    private SmsNotificationService service;

    private static NotificationRequest requestFor(Long userId) {
        return new NotificationRequest(
                List.of(new RecipientRef(userId)), null, null, List.of(new ContentPart("text/plain", "Hi")));
    }

    @Test
    void publishesEventWithUserPhoneAndReturnsQueued() {
        when(userCacheService.getUserContact(1L)).thenReturn(new UserContact(1L, null, "+15551234567", List.of()));

        var response = service.send(requestFor(1L));

        assertThat(response.status()).isEqualTo(SendStatus.QUEUED);
        assertThat(response.notificationId()).isNotBlank();

        var eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventProducer).send(eventCaptor.capture());
        assertThat(eventCaptor.getValue().phoneNumber()).isEqualTo("+15551234567");
        assertThat(eventCaptor.getValue().channel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    void throwsWhenUserHasNoPhone() {
        when(userCacheService.getUserContact(1L)).thenReturn(new UserContact(1L, "a@example.com", null, List.of()));

        assertThatThrownBy(() -> service.send(requestFor(1L))).isInstanceOf(NotificationBadRequestException.class);
    }

    @Test
    void propagatesUserNotFound() {
        when(userCacheService.getUserContact(99L)).thenThrow(new NotificationBadRequestException("User not found: 99"));

        assertThatThrownBy(() -> service.send(requestFor(99L))).isInstanceOf(NotificationBadRequestException.class);
    }
}
