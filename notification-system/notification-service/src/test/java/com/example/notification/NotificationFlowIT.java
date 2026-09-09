package com.example.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.notification.domain.Device;
import com.example.notification.domain.Platform;
import com.example.notification.domain.User;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.dto.SendStatus;
import com.example.notification.repository.DeviceRepository;
import com.example.notification.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Smoke test: all three channel endpoints, end to end, against H2 + stub providers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class NotificationFlowIT {

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private DeviceRepository deviceRepository;

  private User user;

  @BeforeEach
  void setUp() {
    user = userRepository.save(new User("user@example.com", "+15551234567"));
    deviceRepository.save(new Device(user, Platform.IOS, "device-token-1"));
  }

  @Test
  void sendsPush() {
    var response = post("/v1/notifications/push", pushBody());

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody().status()).isEqualTo(SendStatus.SENT);
  }

  @Test
  void sendsSms() {
    var response = post("/v1/notifications/sms", contentOnlyBody());

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody().status()).isEqualTo(SendStatus.SENT);
  }

  @Test
  void sendsEmail() {
    var response = post("/v1/notifications/email", emailBody());

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody().status()).isEqualTo(SendStatus.SENT);
  }

  private org.springframework.http.ResponseEntity<NotificationResponse> post(String path, String body) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate.postForEntity(
        "http://localhost:" + port + path, new HttpEntity<>(body, headers), NotificationResponse.class);
  }

  private String pushBody() {
    return """
        {
          "to": [{"user_id": %d}],
          "subject": "Hello",
          "content": [{"type": "text/plain", "value": "Hi there"}]
        }
        """
        .formatted(user.getId());
  }

  private String contentOnlyBody() {
    return """
        {
          "to": [{"user_id": %d}],
          "content": [{"type": "text/plain", "value": "Hi there"}]
        }
        """
        .formatted(user.getId());
  }

  private String emailBody() {
    return """
        {
          "to": [{"user_id": %d}],
          "from": {"email": "noreply@example.com"},
          "subject": "Hello",
          "content": [{"type": "text/plain", "value": "Hi there"}]
        }
        """
        .formatted(user.getId());
  }
}
