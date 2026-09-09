package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.domain.Device;
import com.example.notification.domain.Platform;
import com.example.notification.domain.User;
import com.example.notification.dto.ContentPart;
import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.RecipientRef;
import com.example.notification.dto.SendStatus;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.provider.PushProvider;
import com.example.notification.provider.SendResult;
import com.example.notification.repository.DeviceRepository;
import com.example.notification.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private PushProvider pushProvider;

    @InjectMocks
    private PushNotificationService service;

    private static NotificationRequest requestFor(Long userId) {
        return new NotificationRequest(
                List.of(new RecipientRef(userId)), null, "Hello", List.of(new ContentPart("text/plain", "Hi")));
    }

    @Test
    void sendsToEveryDeviceAndReturnsSentWhenAnySucceeds() {
        var user = new User("a@example.com", null);
        var device1 = new Device(user, Platform.IOS, "token-1");
        var device2 = new Device(user, Platform.ANDROID, "token-2");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(deviceRepository.findByUserId(1L)).thenReturn(List.of(device1, device2));
        when(pushProvider.send(any())).thenReturn(SendResult.success("msg-1"));

        var response = service.send(requestFor(1L));

        assertThat(response.status()).isEqualTo(SendStatus.SENT);
        assertThat(response.providerMessageId()).isEqualTo("msg-1");
        verify(pushProvider, times(2)).send(any());
    }

    @Test
    void returnsFailedWhenEveryDeviceSendFails() {
        var user = new User("a@example.com", null);
        var device = new Device(user, Platform.IOS, "token-1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(deviceRepository.findByUserId(1L)).thenReturn(List.of(device));
        when(pushProvider.send(any())).thenReturn(SendResult.failure("provider down"));

        var response = service.send(requestFor(1L));

        assertThat(response.status()).isEqualTo(SendStatus.FAILED);
        assertThat(response.error()).isEqualTo("provider down");
    }

    @Test
    void throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(requestFor(99L))).isInstanceOf(NotificationBadRequestException.class);
    }

    @Test
    void throwsWhenUserHasNoDevices() {
        var user = new User("a@example.com", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(deviceRepository.findByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.send(requestFor(1L))).isInstanceOf(NotificationBadRequestException.class);
    }
}
