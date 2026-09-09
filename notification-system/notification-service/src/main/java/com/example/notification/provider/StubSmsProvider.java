package com.example.notification.provider;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Logs the payload and fakes a provider response. Swap for a real SMS gateway client later. */
@Component
@Slf4j
public class StubSmsProvider implements SmsProvider {

  @Override
  public SendResult send(SmsSendCommand command) {
    log.info("Stub SMS send: phoneNumber={} content={}", command.phoneNumber(), command.content());
    return SendResult.success("stub-sms-" + UUID.randomUUID());
  }
}
