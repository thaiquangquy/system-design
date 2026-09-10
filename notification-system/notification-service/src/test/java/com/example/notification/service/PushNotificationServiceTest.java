package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.cache.UserCacheService;
import com.example.notification.cache.UserContact;
import com.example.notification.domain.Platform;
import com.example.notification.dto.ContentPart;
import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.RecipientRef;
import com.example.notification.dto.SendStatus;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.messaging.NotificationEvent;
import com.example.notification.messaging.NotificationEventProducer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private NotificationEventProducer eventProducer;

    @InjectMocks
    private PushNotificationService service;

    private static NotificationRequest requestFor(Long userId) {
        return new NotificationRequest(
                List.of(new RecipientRef(userId)), null, "Hello", List.of(new ContentPart("text/plain", "Hi")));
    }

    @Test
    void publishesEventForEveryDeviceAndReturnsQueued() {
        var contact = new UserContact(
                1L,
                "a@example.com",
                null,
                List.of(
                        new UserContact.Device("token-1", Platform.IOS),
                        new UserContact.Device("token-2", Platform.ANDROID)));
        when(userCacheService.getUserContact(1L)).thenReturn(contact);

        var response = service.send(requestFor(1L));

        assertThat(response.status()).isEqualTo(SendStatus.QUEUED);
        assertThat(response.notificationId()).isNotBlank();

        var eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventProducer).send(eventCaptor.capture());
        var event = eventCaptor.getValue();
        assertThat(event.channel()).isEqualTo(NotificationChannel.PUSH);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.devices()).hasSize(2);
        assertThat(event.notificationId().toString()).isEqualTo(response.notificationId());
    }

    @Test
    void throwsWhenUserHasNoDevices() {
        when(userCacheService.getUserContact(1L)).thenReturn(new UserContact(1L, "a@example.com", null, List.of()));

        assertThatThrownBy(() -> service.send(requestFor(1L))).isInstanceOf(NotificationBadRequestException.class);
    }

    @Test
    void propagatesUserNotFound() {
        when(userCacheService.getUserContact(99L)).thenThrow(new NotificationBadRequestException("User not found: 99"));

        assertThatThrownBy(() -> service.send(requestFor(99L))).isInstanceOf(NotificationBadRequestException.class);
    }
}
