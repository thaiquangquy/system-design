package com.example.notification.provider;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Logs the payload and fakes a provider response. Swap for a real APNs/FCM client later. */
@Component
@Slf4j
public class StubPushProvider implements PushProvider {

  @Override
  public SendResult send(PushSendCommand command) {
    log.info(
        "Stub push send: platform={} token={} title={} body={}",
        command.platform(),
        command.deviceToken(),
        command.title(),
        command.body());
    return SendResult.success("stub-push-" + UUID.randomUUID());
  }
}
