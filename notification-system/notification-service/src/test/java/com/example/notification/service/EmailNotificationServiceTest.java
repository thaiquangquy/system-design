package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.notification.domain.User;
import com.example.notification.dto.ContentPart;
import com.example.notification.dto.NotificationRequest;
import com.example.notification.dto.RecipientRef;
import com.example.notification.dto.SendStatus;
import com.example.notification.dto.Sender;
import com.example.notification.exception.NotificationBadRequestException;
import com.example.notification.provider.EmailProvider;
import com.example.notification.provider.SendResult;
import com.example.notification.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailProvider emailProvider;

    @InjectMocks
    private EmailNotificationService service;

    private static NotificationRequest requestFor(Long userId) {
        return new NotificationRequest(
                List.of(new RecipientRef(userId)),
                new Sender("noreply@example.com"),
                "Subject",
                List.of(new ContentPart("text/plain", "Hi")));
    }

    @Test
    void sendsToUserEmailAndReturnsSent() {
        var user = new User("a@example.com", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(emailProvider.send(any())).thenReturn(SendResult.success("msg-1"));

        var response = service.send(requestFor(1L));

        assertThat(response.status()).isEqualTo(SendStatus.SENT);
        assertThat(response.providerMessageId()).isEqualTo("msg-1");
    }

    @Test
    void returnsFailedWhenProviderFails() {
        var user = new User("a@example.com", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(emailProvider.send(any())).thenReturn(SendResult.failure("provider down"));

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
    void throwsWhenUserHasNoEmail() {
        var user = new User(null, "+15551234567");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.send(requestFor(1L))).isInstanceOf(NotificationBadRequestException.class);
    }
}
